package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.dataflow.fieldsensitive.AccessPathNode;
import basic.dataflow.fieldsensitive.AliasGraph;
import soot.Value;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class RecordLLMExplainRule implements Rule  {
    @Override
    public void apply(Transformable transformable) {
        if(TransformableNode.recordExplain) {
            TransformableNode transformableNode = (TransformableNode) transformable;
            MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);

            AliasGraph aliasGraph = methodDescriptor.aliasGraph;
            List<String> allTainted = new LinkedList<>();
            for(AccessPathNode root : aliasGraph.roots) {
                List<String> taintedAccessPaths = aliasGraph.getTaintedAccessPaths(root);
                allTainted.addAll(taintedAccessPaths);
            }
            if(allTainted.size() > 20) {
                // hack： 截断一下，可能爆炸了
                allTainted = allTainted.subList(0, 20);
            }
            if(allTainted.size() > 0) {
                transformableNode.explainMessage2 += "\n此时：被污染的变量别名信息包括: " + allTainted.toString() + "\n";
            }

            if(!methodDescriptor.allTaintNodes.isEmpty()) {
                // 这里用message2来记录，因为message1是用来记录存在污点改变行为的，我们可能用1非空来判断是否需要保存这一条
                transformableNode.explainMessage2 += "此时的污点集合：";
                for (TaintNode taintNode : methodDescriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    transformableNode.explainMessage2 += taintNode.value.toString() + "; ";
                }
                transformableNode.explainMessage2 += "\n";
            }

            // 应该和上面是一样的
//            Set<Value> taintVals = methodDescriptor.getVal_m_taintNode().keySet();
//            if(!taintVals.isEmpty()) {
//                // 这里用message2来记录，因为message1是用来记录存在污点改变行为的，我们可能用1非空来判断是否需要保存这一条
//                transformableNode.explainMessage2 += "此时的污点集合(从Val_m_taintNode读)：";
//                for (Value value : taintVals) {
//                    transformableNode.explainMessage2 += value.toString() + "; ";
//                }
//                transformableNode.explainMessage2 += "\n";
//            }
        }
    }

    @Override
    public String getName() {
        return "RecordLLMExplainRule";
    }
}
