package detect.summary;

import basic.cg.CG;
import detect.DetectContext;
import detect.summary.datastructure.DetectContextDependency;
import intelligent.IntelligentLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@Component
public class CGSegmenter {

    @Autowired
    IntelligentLogger intelligentLogger;

    public HashMap<Integer, DetectContext> id2DetectContext = new HashMap<>();

    public HashMap<SootMethod, List<Integer>> method2ContextIds = new HashMap<>(); // 记录一个方法属于哪些detextContext

    public static List<String> noImportMethods = Arrays.asList("init", "toString", "equals", "hashCode", "getClass", "<init>", "<clinit>", "debug", "error", "log", "trace", "warn", "info", "printStackTrace");

    // 这个方法处理detectContext之间的调用relation，把hazard位置标记，并最终返回排序后的detectContext
    public List<DetectContext> segment(CG cg) {
        // 先统计每个detectContext， 1-n层调用的方法有哪些
        System.out.println("开始分割call graph(先统计每个detectContext， 1-n层调用的方法有哪些)...");
        int con = 0;
        for(DetectContext detectContext : id2DetectContext.values()) {
//            if(con++ > 10) {
//                break;
//            }
//            System.out.println("当前的detectContext是：" + detectContext.id);
            List<SootMethod> visited = new LinkedList<>();
            List<SootMethod> next = new LinkedList<>();
            List<DetectContextDependency.MethodDependency> currentDependencySet = new LinkedList<>();
            List<SootMethod> callStack = detectContext.getCallStack();
            for (int i = 0; i < callStack.size(); i++) {
                SootMethod sootMethod = callStack.get(i);
                currentDependencySet.add(new DetectContextDependency.MethodDependency(sootMethod, i, 0));
            }
            for(int i = 0; i < 4; i++) {
//                System.out.println("第" + i + "层调用的方法有：");
                for(DetectContextDependency.MethodDependency dependency : currentDependencySet) {
//                    System.out.println(dependency.sootMethod.toString());
                    detectContext.getDetectContextDependency().methodToDepth.put(dependency.sootMethod, i);
                    // 准备后面把主栈上的0层方法不建立relation，所以这里不添加到method2ContextIds
                    if(i > 0) {
                        if(!method2ContextIds.containsKey(dependency.sootMethod)) {
                            method2ContextIds.put(dependency.sootMethod, new LinkedList<>());
                        }
                        method2ContextIds.get(dependency.sootMethod).add(detectContext.id);
                    }
                    if(!detectContext.getDetectContextDependency().depth2Dependencies.containsKey(i)) {
                        detectContext.getDetectContextDependency().depth2Dependencies.put(i, new LinkedList<>());
                    }
                    detectContext.getDetectContextDependency().depth2Dependencies.get(i).add(dependency);
                    detectContext.getDetectContextDependency().methodToDependency.put(dependency.sootMethod, dependency);
                }
                for(DetectContextDependency.MethodDependency dependency : currentDependencySet) {
                    visited.add(dependency.sootMethod);
                }

                // 从当前的dependency中找到下一层的dependency
                List<DetectContextDependency.MethodDependency> nextDependencySet = new LinkedList<>();
                for(DetectContextDependency.MethodDependency methodDependency : currentDependencySet) {
                    SootMethod sootMethod = methodDependency.sootMethod;
                    cg.edgesOutOf(sootMethod).forEach(tgt -> {
                        if(tgt.getSignature().startsWith("<java")) return;
                        if(noImportMethods.contains(tgt.getName())) return;
                        if(!visited.contains(tgt) && !next.contains(tgt)) {
                            DetectContextDependency.MethodDependency newDependency = new DetectContextDependency.MethodDependency(tgt, methodDependency.hang_depth, methodDependency.branch_depth + 1);
                            nextDependencySet.add(newDependency);
                            next.add(tgt);
                        }
                    });
                }
                if(nextDependencySet.isEmpty()) {
                    break;
                }
                currentDependencySet = nextDependencySet;

                intelligentLogger.logFileRPO("detectContext是：" + detectContext.id);
                intelligentLogger.logFileRPO("方法depencency");
                for(Integer depth : detectContext.getDetectContextDependency().depth2Dependencies.keySet()) {
                    intelligentLogger.logFileRPO("branch_depth: " + depth);
                    List<DetectContextDependency.MethodDependency> methodDependencies = detectContext.getDetectContextDependency().depth2Dependencies.get(depth);
                    intelligentLogger.logFileRPO("这个detextContext中branch_depth为此的方法个数: " + methodDependencies.size());
                }
            }
        }

        // 现在开始考虑detectContext之间的依赖关系, 此时不确定方法归属，所以先记录context1,2通过方法m相互依赖，将来确定归属后，context1,2形成偏序
        List<DetectContextDependency.Relationship> allRelations = new LinkedList<>();
        for(SootMethod sootMethod : method2ContextIds.keySet()) {
            List<Integer> contextIds = method2ContextIds.get(sootMethod);
            if(contextIds.size() > 1) {
//                System.out.println("一个方法属于多个detectContext: " + sootMethod.toString() + " " + contextIds.size());
//                System.out.println("contextIds: " + contextIds.toString());
                for(int i = 0; i < contextIds.size(); i++) {
                    for(int j = i + 1; j < contextIds.size(); j++) {
                        DetectContext context1 = id2DetectContext.get(contextIds.get(i));
                        DetectContext context2 = id2DetectContext.get(contextIds.get(j));
                        DetectContextDependency.Relationship relationship = new DetectContextDependency.Relationship(context1, context2, sootMethod);
                        context1.getDetectContextDependency().related.add(relationship);
                        context2.getDetectContextDependency().related.add(relationship);
                        allRelations.add(relationship);
                    }
                }
            }
        }
        for(DetectContextDependency.Relationship relationship : allRelations) {
            intelligentLogger.logFileRPO("现在开始考虑detectContext之间的依赖关系, 此时不确定方法归属，所以先记录context1,2通过方法m相互依赖，将来确定归属后，context1,2形成偏序");
            intelligentLogger.logFileRPO("context1: " + relationship.detectContext_left.id + " context2: " + relationship.detectContext_right.id + "通过这个 method: " + relationship.bridgeMethod.toString());
        }
        // 取最后冒险点hang_depth最浅的detectContext先出来，因此这里进行排序
        for(DetectContext detectContext : id2DetectContext.values()) {
            DetectContextDependency detectContextDependency = detectContext.getDetectContextDependency();
            detectContextDependency.sortLastRiskyChain();
            intelligentLogger.logFileRPO("detectContext: " + detectContext.id + "最后冒险点的排序");
            LinkedList<DetectContextDependency.Relationship> lastRiskyChain = detectContextDependency.riskyChain;
            if(lastRiskyChain.isEmpty()) {
                intelligentLogger.logFileRPO("这个detectContext没有冒险点");
            } else {
                DetectContextDependency.Relationship last = lastRiskyChain.getLast();
                intelligentLogger.logFileRPO("最后冒险点的hang_depth " + detectContextDependency.relationMapHangDepth.get(last));
            }
        }
        // 现在开始确定detectContext的次序
        List<DetectContext> sortedDetectContext = new LinkedList<>();
        List<DetectContext> waitingList = new LinkedList<>(id2DetectContext.values());
        while (!waitingList.isEmpty()) {

            // 不知这里是否要考虑，如果有detectContext的冒险被求解完了，优先把它再分析掉

            // 优先没有冒险点的，如果都有冒险，取最后冒险点hang_depth最浅的detectContext先出来
            int miniHang = Integer.MAX_VALUE;
            DetectContext selectedDetectContext = null;
            for(DetectContext detectContext : waitingList) {
                DetectContextDependency detectContextDependency = detectContext.getDetectContextDependency();
                if(detectContextDependency.riskyChain.isEmpty()) {
                    // 此时说明没有冒险点，直接出队
                    miniHang = 0;
                    selectedDetectContext = detectContext;
                } else {
                    DetectContextDependency.Relationship last = detectContextDependency.riskyChain.getLast();
                    int hang_depth = detectContextDependency.relationMapHangDepth.get(last);
                    if(hang_depth < miniHang) {
                        miniHang = hang_depth;
                        selectedDetectContext = detectContext;
                    }
                }
            }
            waitingList.remove(selectedDetectContext);
            sortedDetectContext.add(selectedDetectContext);
//            System.out.println("selectedDetectContext: " + selectedDetectContext.id + " miniHang: " + miniHang);
            // 把处理这个detectContext时要冒险的方法做记录，等待分析时读取
            selectedDetectContext.getDetectContextDependency().analysisRiskyChain = new LinkedList<>(selectedDetectContext.getDetectContextDependency().riskyChain);

            // 这时，要把处理过的context相关的relation更新到relation另一侧的链表：另一侧这个relation应去掉，因为另一侧到这个方法的incoming states已经计算
            DetectContextDependency detectContextDependency = selectedDetectContext.getDetectContextDependency();
            for(DetectContextDependency.Relationship relationship : detectContextDependency.related) {
                DetectContext anotherDetectContext = relationship.detectContext_left == selectedDetectContext ? relationship.detectContext_right : relationship.detectContext_left;
                DetectContextDependency anotherDetectContextDetectContextDependency = anotherDetectContext.getDetectContextDependency();
                anotherDetectContextDetectContextDependency.riskyChain.remove(relationship);
            }
        }
        return sortedDetectContext;
    }

}
