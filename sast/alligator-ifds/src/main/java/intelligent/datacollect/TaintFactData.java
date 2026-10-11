package intelligent.datacollect;

import TranModel.TransformableNode;
import TranModel.datastruct.TaintEdge;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class TaintFactData {

    public static HashSet<String> dataflowFactInMethod(MethodDescriptor methodDescriptor) {
        HashSet<String> ret = new HashSet<>();
        for(TaintNode taintNode : methodDescriptor.allTaintNodes) {
            for(TaintEdge taintEdge : taintNode.outs) {
                StringBuilder str = new StringBuilder();
                str.append("在这条语句：" + taintEdge.transformableNode.node.unit + "\n");
                str.append("有这样的污点传播:");
                str.append(taintEdge.from.getValueStr());
                str.append(" -> ");
                str.append(taintEdge.to.getValueStr());

                ret.add(str.toString());
            }
        }
        return ret;
    }

    public static String collectMethodInfo(MethodDescriptor methodDescriptor, List<TransformableNode> topologicalOrder) {
        StringBuilder str = new StringBuilder();
//        str.append("方法名：" + methodDescriptor.sootMethod.getName());
//        str.append("方法签名：" + methodDescriptor.sootMethod.getSignature());
//        str.append("\n");

        for(TransformableNode transformableNode : topologicalOrder) {
            if(!transformableNode.explainMessage.equals("")) {
                str.append("在这条语句：" + transformableNode.node.unit + "\n");
                str.append(transformableNode.explainMessage + "\n");
                str.append(transformableNode.explainMessage2 + "\n");
                str.append("-----------------------------------\n");
            }

        }

        return str.toString();
    }

}
