package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.dataflow.fieldsensitive.AliasGraph;
import basic.util.StaticAnalyzeUtils.Parameter;
import soot.jimple.IfStmt;

public class TimeDelayRule implements Rule {
    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.method);

        // 指针分析状态表（aliasGraph）的状态计算
        AliasGraph aliasGraph = methodDescriptor.aliasGraph;
        int aliasGraph_state_size = aliasGraph.graphSize();
        int graphSize_tainted = aliasGraph.graphSize_tainted();

        // 污点传播状态表（taintNode）的状态计算
        int taintNodeSize = methodDescriptor.getTaintNodeSize();
        int taint_state_size = taintNodeSize + 3 * graphSize_tainted; // 近似：别名树上的污点在传播时需要经过，查找、传播、merge三个操作

        int stateSize = aliasGraph_state_size + taint_state_size;
        try {
//            System.out.println("数据传播: " + stateSize + "ms");
            Thread.sleep(stateSize); // 50ms * 状态大小
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getName() {
        return "timedelay（这里主动添加了时延！）";
    }
}
