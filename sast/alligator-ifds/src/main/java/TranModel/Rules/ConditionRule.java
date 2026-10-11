package TranModel.Rules;

import TranModel.InfoRule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.util.StaticAnalyzeUtils.Parameter;
import detect.dataflow.DataFlowTrack;
import detect.unit.instrument.InstrumentUtil;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;

import java.util.HashMap;
import java.util.HashSet;

public class ConditionRule implements InfoRule {
    public static HashMap<Integer, TransformableNode> conditionTfNodesMap = new HashMap<>();
    public static HashMap<TransformableNode, ConditionNode> tfMapConditionNode = new HashMap<>();
    public static HashMap<Integer, TransformableNode> conditionMapSuc = new HashMap<>();
    @Override
    public void apply(Transformable transformable, DataFlowTrack.DataFlowResult dataFlowResult) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        IfStmt ifStmt = Parameter.getIfStmt(transformableNode);
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.method);

        // 记录 “污染源” 被使用的情况
        InstrumentUtil.recordUsedFields(transformableNode, methodDescriptor, dataFlowResult);

        if (ifStmt == null)
            return;

        Stmt target = ifStmt.getTarget();
        conditionTfNodesMap.put(ifStmt.hashCode(), transformableNode);

        ConditionNode newConditionNode = new ConditionNode(transformableNode, methodDescriptor, false);
//        dataFlowResult.conditionNodes.add(newConditionNode);
        putConditionNode(dataFlowResult, newConditionNode);
        tfMapConditionNode.put(transformableNode, newConditionNode);

        for (TransformableNode success: transformableNode.successors){
            if (((Stmt)success.node.unit).equals(target) ){
                success.path_record.add(ifStmt.hashCode());
                conditionMapSuc.put(ifStmt.hashCode(), success);
            }else {
                success.path_record.add(-ifStmt.hashCode());
                conditionMapSuc.put(-ifStmt.hashCode(), success);
            }
        }
    }

    public static void putConditionNode(DataFlowTrack.DataFlowResult dataFlowResult, ConditionNode conditionNode){
        HashSet<ConditionNode> toChange = new HashSet<>();
        boolean toAdd = true;

        for (ConditionNode recordedConditionNode: dataFlowResult.conditionNodes){
            if (recordedConditionNode.values.size() != conditionNode.values.size()
                    | recordedConditionNode.constants.size() != conditionNode.constants.size())
                continue;

            if (!recordedConditionNode.values.containsAll(conditionNode.values)
                    | !recordedConditionNode.constants.entrySet().containsAll(conditionNode.constants.entrySet()))
                continue;

            if (recordedConditionNode.basic) {
                if (!recordedConditionNode.isNotContradictory(conditionNode.comparison))
                    toChange.add(recordedConditionNode);
            }
            toAdd = false;
        }

        if (toAdd){
            dataFlowResult.conditionNodes.add(conditionNode);
        }

        for (ConditionNode recordedConditionNode: toChange){
            recordedConditionNode.basic = false;
        }
    }
}
