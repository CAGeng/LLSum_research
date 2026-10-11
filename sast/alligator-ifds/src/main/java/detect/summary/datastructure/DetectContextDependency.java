package detect.summary.datastructure;

import detect.DetectContext;
import soot.SootMethod;

import java.util.*;

public class DetectContextDependency {
    public DetectContext detectContext;
    public HashMap<SootMethod, Integer> methodToDepth = new HashMap<>(); // 方法到depth
    public HashMap<SootMethod, MethodDependency> methodToDependency = new HashMap<>(); // 方法到dependency
    public HashMap<Integer, List<MethodDependency>> depth2Dependencies = new HashMap<>(); // 每一层的dependency

    public HashSet<Relationship> related = new HashSet<>();

    public LinkedList<Relationship> riskyChain = new LinkedList<>(); // 这个用作排序时的记录
    public LinkedList<Relationship> analysisRiskyChain = new LinkedList<>(); // 这个用作指导实际分析

    public HashMap<Relationship, Integer> relationMapHangDepth = new HashMap<>();

    public DetectContextDependency(DetectContext detectContext) {
        this.detectContext = detectContext;
    }

    // 将每个context下要被risky的外部related排序到一个链表
    public void sortLastRiskyChain() {
        // 先把relation里关于方法depth的字段补齐
        for(Relationship relationship : related) {
            MethodDependency methodDependency = methodToDependency.get(relationship.bridgeMethod);
            // 这里看不出left/right谁是当前context
            if(relationship.detectContext_left == this.detectContext) {
                relationship.branch_depth_left = methodDependency.branch_depth;
                relationship.hang_depth_left = methodDependency.hang_depth;
                relationMapHangDepth.put(relationship, methodDependency.hang_depth);
            } else {
                relationship.branch_depth_right = methodDependency.branch_depth;
                relationship.hang_depth_right = methodDependency.hang_depth;
                relationMapHangDepth.put(relationship, methodDependency.hang_depth);
            }
        }

        // 现在进行排序
//        System.out.println("开始排序");
//        System.out.println("size: " + related.size());
        // 使用内置排序方法对关系链按深度排序
        riskyChain = new LinkedList<>(related);
        riskyChain.sort(Comparator.comparingInt(relationMapHangDepth::get));
    }

    public static class MethodDependency {
        public SootMethod sootMethod;
        public int hang_depth; // 主栈悬挂点离开entry的深度
        public int branch_depth; // 离开主调用栈的深度

        public MethodDependency(SootMethod sootMethod, int hang_depth, int branch_depth) {
            this.sootMethod = sootMethod;
            this.hang_depth = hang_depth;
            this.branch_depth = branch_depth;
        }
    }

    public static class Relationship {
        public DetectContext detectContext_left, detectContext_right;
        public int branch_depth_left, branch_depth_right, hang_depth_left, hang_depth_right;
        public SootMethod bridgeMethod;

        public Relationship(DetectContext context1, DetectContext context2, SootMethod sootMethod) {
            this.detectContext_left = context1;
            this.detectContext_right = context2;
            this.bridgeMethod = sootMethod;
        }
    }
}
