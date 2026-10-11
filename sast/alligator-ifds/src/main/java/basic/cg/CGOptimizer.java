package basic.cg;

import app.context.SpringContextUtils;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.interfaces.Singleton;
import intelligent.IntelligentLogger;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.Chain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * 对于CG中缺失的调用边，尝试通过CFG来补全
 * 只针对对特定方法集合(targetMethods)内方法的调用进行补全
 * <a href="https://vwptc6ky5dr.feishu.cn/docx/KXR3dn0fzoBem1xdxjDcItGGnNg?from=from_copylink">CG优化记录</a>
 */

@Slf4j
public class CGOptimizer implements Singleton {
    public static List<SootMethod> targetMethods;
    public static CG cg;

    /**
     * 补充一些断边，比如
     */
    public HashMap<SootMethod, SootMethod> linkMethodMap = new HashMap<>();



    // 所有优化过的方法
    public HashSet<SootMethod> optimizedMethods = new HashSet<>();
    public static HashMap<SootClass, SootMethod> toBeOptimizedInitialMethods = new HashMap<>();

    public CGOptimizer(List<SootMethod> targetMethods, CG cg) {
        this.targetMethods = targetMethods;
        this.cg = cg;

        try {
            // 这些由于CG构建时的断边，人工补充连接上
            SootMethod sootMethod = Scene.v().getMethod("<org.apache.catalina.authenticator.FormAuthenticator: void saveRequest(org.apache.catalina.connector.Request,org.apache.catalina.Session)>");
            targetMethods.add(sootMethod);
            for(SootClass sootClass : Scene.v().getApplicationClasses()) {
                if(sootClass.getName().equals("org.apache.catalina.connector.Request")) {
                    for(SootMethod method : sootClass.getMethods()) {
                        targetMethods.add(method); // 意味着这些方法看到调用一定会被连接
                    }
                }
            }
        } catch (Exception ignored){}

        // 意味着当调用到key的call-site，用value来替代
        HashMap<String, String> linkMethodStrMap = new HashMap<>();
        linkMethodStrMap.put("<jakarta.servlet.http.HttpServletRequest: jakarta.servlet.http.HttpSession getSession(boolean)>",
                "<org.eclipse.jetty.ee9.nested.Request: jakarta.servlet.http.HttpSession getSession(boolean)>");

        for(String key : linkMethodStrMap.keySet()) {
            try {
                // 这些由于CG构建时的断边，人工补充连接上
                SootMethod sootMethod = Scene.v().getMethod(key);
                SootMethod tgtMethod = Scene.v().getMethod(linkMethodStrMap.get(key));
                linkMethodMap.put(sootMethod, tgtMethod);
            } catch (Exception ignored){}
        }
    }

    public void optimizeCG() {
        Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
        for(SootClass sootClass : applicationClasses) {
            if(sootClass.isJavaLibraryClass()) continue;
            List<SootMethod> methods = new ArrayList<>(sootClass.getMethods());
            for(SootMethod sootMethod : methods) {
                if(sootMethod.isPhantom() || !sootMethod.hasActiveBody()) continue;
                CFG cfg = new CFG(sootMethod, true).buildCFG();
                optimizeMethod(sootMethod, cfg);
            }
        }

        // 增加特有的初始化方法的CG连边，我们的方法时在调用clinit的地方增加连接新的方法
        IntelligentLogger logger = SpringContextUtils.getBean(IntelligentLogger.class);
        for(SootClass sootClass : applicationClasses) {
            SootMethod init = null;
            for(SootMethod method : sootClass.getMethods()) {
                if(method.getName().equals("init")) {
                    init = method;
                    break;
                }
            }
            if(init != null) {
                logger.logCGOpt("init method: " + init);
                optimizeClinitAddTargetMethod(init, sootClass);
                toBeOptimizedInitialMethods.put(sootClass, init);
            }
        }
    }

    public void optimizeMethod(SootMethod method, CFG cfg) {
        IntelligentLogger logger = SpringContextUtils.getBean(IntelligentLogger.class);
        for(Node node : cfg.allNodes.values()) {
            Stmt stmt = (Stmt) node.unit;
            if(stmt.containsInvokeExpr() && cg.calleeOutOf(node.unit).isEmpty()) {
                SootMethod invokeMethod = stmt.getInvokeExpr().getMethod();
                if(targetMethods.contains(invokeMethod)) {
                    Edge edge = new Edge(method, stmt, invokeMethod);
                    cg.callGraph.addEdge(edge);
                    optimizedMethods.add(method);
//                    log.info("optimize method: " + method + " add edge: " + edge);
                    logger.logCGOpt("optimize method: " + method + " add edge: " + edge);
                }
                // 替换的调用边
                for(SootMethod key : linkMethodMap.keySet()) {
                    SootMethod value = linkMethodMap.get(key);
                    if(key.equals(invokeMethod)) {
                        Edge edge = new Edge(method, stmt, value);
                        cg.callGraph.addEdge(edge);
                        optimizedMethods.add(method);
//                        log.info("optimize method(替换一个调用): " + method + " add edge: " + edge);
                        logger.logCGOpt("optimize method(替换一个调用): " + method + " add edge: " + edge);
                    }
                }
            } else{
                // 替换的调用边
                List<SootMethod> invokeMethods = cg.calleeOutOf(node.unit);
                for(SootMethod key : linkMethodMap.keySet()) {
                    SootMethod value = linkMethodMap.get(key);
                    if(invokeMethods.contains(key) && !invokeMethods.contains(value)) {
                        Edge edge = new Edge(method, stmt, value);
                        cg.callGraph.addEdge(edge);
                        optimizedMethods.add(method);
                        log.info("optimize method(替换一个调用): " + method + " add edge: " + edge);
                        logger.logCGOpt("optimize method(替换一个调用): " + method + " add edge: " + edge);
                    }
                }
            }
        }
    }

    // (没有启用) 在这个类的clinit的所有调用处添加对targetM的调用
    public void optimizeClinitAddTargetMethod(SootMethod targetM, SootClass sootClass) {
        IntelligentLogger logger = SpringContextUtils.getBean(IntelligentLogger.class);
        // 把这项优化推迟到dataflowtrack里吧。
//        SootMethod clinitM = sootClass.getMethodByNameUnsafe("<clinit>");
//        if(clinitM == null) return;
//        List<SootMethod> callerMethods = cg.callerIntoMethod(clinitM);
//        List<Unit> callerUnits = cg.callerUnitIntoMethod(clinitM);
//        for(SootMethod callerMethod : callerMethods) {
//            CFG cfg = new CFG(callerMethod, true).buildCFG();
//            for(Unit unit : cfg.allNodes.keySet()) {
//                Stmt stmt = (Stmt) unit;
//                if(stmt.containsInvokeExpr()) {
//                    if(callerUnits.contains(stmt) && !cg.calleeOutOf(stmt).contains(targetM)) {
//                        Edge edge = new Edge(callerMethod, stmt, targetM);
//                        cg.callGraph.addEdge(edge);
//                        optimizedMethods.add(callerMethod);
//                        log.info("optimize initialize method: " + callerMethod + " add edge: " + edge);
//                        logger.logCGOpt("optimize initialize method: " + callerMethod + " add edge: " + edge);
//                    }
//                }
//            }
//        }
    }

    private static class SingletonHolder {
        private static final CGOptimizer INSTANCE = new CGOptimizer(targetMethods, cg);
    }

    public static CGOptimizer getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
