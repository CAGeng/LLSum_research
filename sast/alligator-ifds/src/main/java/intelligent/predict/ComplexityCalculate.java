package intelligent.predict;

import TranModel.TranUtil;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.cg.CG;
import basic.dataflow.fieldsensitive.AliasGraph;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Component
public class ComplexityCalculate {

    public static CG cg;

    public int calculateComplexity(MethodDescriptor methodDescriptor, TransformableNode transformableNode) {

        /**
         * // Delta-complexity = context_complexity * state_size
         */

        // 来自前向上下文中的复杂度
//        int context_complexity = getForwardContextComplexity(transformableNode);
        // 来自前向上下文中的复杂度 简单算法
        int context_complexity = 0;
        for(SootMethod invokeMethod : transformableNode.invokeMethods()) {
            int callees = cg.calleeOutOfMethod(invokeMethod).size();
            context_complexity = callees;
        }
        // 状态表大小
        int state_size = calculateStateSize(methodDescriptor, transformableNode);

        return context_complexity * state_size;
    }

    public int calculateStateSize(MethodDescriptor methodDescriptor, TransformableNode transformableNode) {
        int stateSize = 0;

        /**
         * // state_size = sum [(call_site_state + return_site_state) * unit_count]
         * // call_site_state = alias_state + taint_state                                         考虑指针分析和污点传播状态，他们占状态表的主要部分
         * // return_site_state = lambda(call_site_taint_size, forward_depth, total_callees)      近似：假设return_site的状态数可以用lambda中三个值的乘积来近似
         */

        // 指针分析状态表（aliasGraph）的状态计算
        AliasGraph aliasGraph = methodDescriptor.aliasGraph;
        int aliasGraph_state_size = aliasGraph.graphSize();
        int graphSize_tainted = aliasGraph.graphSize_tainted();

        // 污点传播状态表（taintNode）的状态计算
        int taintNodeSize = methodDescriptor.getTaintNodeSize();
        int taint_state_size = taintNodeSize + 3 * graphSize_tainted; // 近似：别名树上的污点在传播时需要经过，查找、传播、merge三个操作

        return aliasGraph_state_size + taint_state_size;
    }

    public int getForwardContextComplexity(TransformableNode transformableNode) {
        visitedMethods = new HashSet<>();
        recursedMethods = new HashSet<>();
        int stackLimit = 5;
        int contextComplexity = getForwardContextComplexity_recurse(transformableNode, 0, stackLimit);
        for(SootMethod recursedMethod : recursedMethods) {
            int recursedMethodComplexity = methodMapComplexity.get(recursedMethod);
            contextComplexity += recursedMethodComplexity * stackLimit; // 递归调用了自己的方法，认为它的复杂度会重复limit那么多次
        }
        return contextComplexity;
    }

    private HashSet<SootMethod> visitedMethods = new HashSet<>(); // 不重复
    private HashSet<SootMethod> recursedMethods = new HashSet<>(); // 递归调用了自己的方法，认为它的复杂度会重复limit那么多次
    private HashMap<SootMethod, Integer> methodMapComplexity = new HashMap<>(); // 递归调用了自己的方法，认为它的复杂度会重复limit那么多次
    public int getForwardContextComplexity_recurse(TransformableNode transformableNode, int depth, int limit) {
        if(depth > limit) return 0;
        HashSet<SootMethod> invokes = transformableNode.invokeMethods();
        int complexity = invokes.size() * depth; // 1. 因素1 不确定性调用数量 ; 因素2 调用栈深度
        for(SootMethod invokeMethod : invokes) {
            if(visitedMethods.contains(invokeMethod)) {
                recursedMethods.add(invokeMethod);
                continue;
            }
            visitedMethods.add(invokeMethod);

            int complexityInMethod = 0;
            CFG cfg = new CFG(invokeMethod).buildCFG();
            List<TransformableNode> topologicalOrderedTNodes = TranUtil.getTopologicalOrderedTNodesFromCFG(cfg);
            for(TransformableNode tNode : topologicalOrderedTNodes) { // 3. 因素2 过程间分支
                if(tNode.containsInvoke()) {
                    complexityInMethod += getForwardContextComplexity_recurse(tNode, depth + 1, limit);
                }
            }
            methodMapComplexity.put(invokeMethod, complexityInMethod);
            complexity += complexityInMethod;
        }
        return complexity;
    }


}
