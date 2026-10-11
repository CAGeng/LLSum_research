package detect.knowledge.sink;

import app.configuration.GeneralConfig;
import basic.cfg.CFG;
import basic.cg.CG;
import basic.util.LogUtil;
import detect.knowledge.Knows;
import intelligent.IntelligentLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.Stmt;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;

/**
 * 根据java 基本sink检索当前项目中的sink wrapper
 */

@Component
@Slf4j
public class SinkDetector {

    @Autowired
    Knows knows;

    @Autowired
    IntelligentLogger intelligentLogger;

    private HashSet<String> sinkMethodSigs = new HashSet<>();
    private HashMap<String, String> method2VulType = new HashMap<>();

    public HashSet<SootMethod> sinkWrappers = new HashSet<>();

    private HashMap<String, String> sigMapJimpleCode = new HashMap<>();

    public HashSet<SootMethod> discoveredSinks = new HashSet<>();
    public CG cg;

    @Autowired
    GeneralConfig generalConfig;

    private SootMethod dummy_newarray;
    public static List<String> DUMMY_CLASS_NAME = Arrays.asList("dummy.dos.array");

    public boolean process_dummy_switcher = false;

    public void detectSinks() {
        dummy_newarray = new SootMethod("newarray", Collections.singletonList(IntType.v()), NullType.v());
        dummy_newarray.setDeclaringClass(new SootClass("dummy.dos.array", Modifier.PUBLIC));
        dummy_newarray.setDeclared(true);

        if(!knows.getSinkTypes().containsKey("boundary_check")) {
            knows.getSinkTypes().put("boundary_check", new LinkedList<>());
        }
        knows.getSinkTypes().get("boundary_check").add(dummy_newarray);


        MethodSinksCollection collection = knows.getCollection();
        collection.methodSinksMap.values().forEach(methodSinks -> {
            sinkMethodSigs.addAll(methodSinks.sinkMethods().stream().map(SootMethod::getSignature).collect(java.util.stream.Collectors.toSet()));
            for(SootMethod sootMethod : methodSinks.sinkMethods()) {
                method2VulType.put(sootMethod.getSignature(), methodSinks.getTypeName());
            }
        });


        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                visitMethod(sootMethod);
            }
        }

        LogUtil.outputToJSON(sigMapJimpleCode, generalConfig.getSootOutputPath(), "sinkWrapperMapBody.json");

    }

    public void visitMethod(SootMethod sootMethod) {
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        cfg.allNodes.values().forEach(node -> {
            Stmt stmt = (Stmt) node.unit;
            if (stmt.containsInvokeExpr()) {
                String signature = stmt.getInvokeExpr().getMethod().getSignature();
                if (sinkMethodSigs.contains(signature)) {
                    if (!sinkWrappers.contains(sootMethod)) {
                        sinkWrappers.add(sootMethod);
                        intelligentLogger.logSinkWrapper(sootMethod.getSignature() + " || " + method2VulType.get(signature) + " ||(calling) " + signature);
                        sigMapJimpleCode.put(sootMethod.getSignature(), sootMethod.retrieveActiveBody().toString());
                    }
                }
            }
        });

        if(process_dummy_switcher){
            processDummySinks(sootMethod, cfg);
        }
    }

    private void processDummySinks(SootMethod sootMethod, CFG cfg) {
        // 处理例如r5 = newarray (org.elasticsearch.action.search.ScrollIdForNode)[$i0];的情况，把它表示为一个dummy方法调用
        cfg.allNodes.values().forEach(node -> {
            Stmt stmt = (Stmt) node.unit;
            if (stmt instanceof soot.jimple.AssignStmt) {
                Value rightOp = ((AssignStmt) stmt).getRightOp();
                if (rightOp.toString().contains("newarray")) {
                    if (!sinkWrappers.contains(sootMethod)) {
                        sinkWrappers.add(sootMethod);
                        intelligentLogger.logSinkWrapper(sootMethod.getSignature() + " || " + "DoS" + " ||(calling) " + "newarray");
                        sigMapJimpleCode.put(sootMethod.getSignature(), sootMethod.retrieveActiveBody().toString());
                    }

                    discoveredSinks.add(dummy_newarray);
                    // optimize cg（第二次optimize）
                    Edge edge = new Edge(sootMethod, stmt, dummy_newarray, Kind.SPECIAL);
                    cg.callGraph.addEdge(edge);
//                    log.warn("匹配到一个模式sink，添加方法和调用" + dummy_newarray + " add edge: " + edge);
                    intelligentLogger.logCGOpt("匹配到一个模式sink，添加方法和调用" + dummy_newarray + " add edge: " + edge);
                }
            }
        });
    }
}
