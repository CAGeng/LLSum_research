package TranModel.state;

import TranModel.Rules.ConditionRule;
import TranModel.Symbol.Comparison;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import detect.dataflow.DataFlowTrack;
import fj.P;

import java.util.List;

public class StateUtil {

    public static TransformableNode.BoundaryCheckState matchBoundaryCheck(ConditionNode conditionNode) {
        // 判断一个condition属不属于边界检查
        if (conditionNode.comparison == Comparison.SMALLER_OR_EQUAL || conditionNode.comparison == Comparison.BIGGER_OR_EQUAL){
            if(!conditionNode.constants.isEmpty() && !conditionNode.paramInds.isEmpty()) {
                return TransformableNode.BoundaryCheckState.ARRAY_BOUNDARY_CHECK;
            }
            if(!conditionNode.constants.isEmpty() && conditionNode.paramInds.isEmpty()) {
                return TransformableNode.BoundaryCheckState.MAYBE_CHECK;
            }
            for(String defSource : conditionNode.defSources) {
                if(defSource.toLowerCase().contains("length") || defSource.toLowerCase().contains("size") || defSource.toLowerCase().contains("max")) {
                    return TransformableNode.BoundaryCheckState.MAYBE_CHECK;
                }
            }
        }
        return TransformableNode.BoundaryCheckState.NO_CHECK;
    }

    // 用于合并两个state，生成其中更强的state
    public static TransformableNode.BoundaryCheckState mergeBoundaryCheckState(TransformableNode.BoundaryCheckState state1, TransformableNode.BoundaryCheckState state2) {
        if(state1 == TransformableNode.BoundaryCheckState.ARRAY_BOUNDARY_CHECK || state2 == TransformableNode.BoundaryCheckState.ARRAY_BOUNDARY_CHECK) {
            return TransformableNode.BoundaryCheckState.ARRAY_BOUNDARY_CHECK;
        }
        if(state1 == TransformableNode.BoundaryCheckState.MAYBE_CHECK || state2 == TransformableNode.BoundaryCheckState.MAYBE_CHECK) {
            return TransformableNode.BoundaryCheckState.MAYBE_CHECK;
        }
        return TransformableNode.BoundaryCheckState.NO_CHECK;
    }

    public static void checkCondition(TransformableNode transformableNode, DataFlowTrack.DataFlowResult dataFlowResult) {
        // 做path的检查，用来匹配分支另一头是否有log.warn/ exception这样的标志，进而识别一些关键的path约束
        for(Integer path_record : transformableNode.path_record) {
            Integer contractPathRec = -path_record;
            if(ConditionRule.conditionMapSuc.containsKey(contractPathRec)) {
                TransformableNode contractNode = ConditionRule.conditionMapSuc.get(contractPathRec);
                if(contractNode.node.unit.toString().toLowerCase().contains("warn") || contractNode.node.unit.toString().toLowerCase().contains("exception")
                || contractNode.node.unit.toString().toLowerCase().contains("error") || contractNode.node.unit.toString().toLowerCase().contains("fail") ||
                        contractNode.node.unit.toString().toLowerCase().contains("logger")) {
                    Integer abs_path_record = Math.abs(path_record);
                    TransformableNode ifNode = ConditionRule.conditionTfNodesMap.get(abs_path_record);
                    ConditionNode conditionNode = ConditionRule.tfMapConditionNode.get(ifNode);
                    for(String defSource : conditionNode.defSources) {
                        if(defSource.toLowerCase().contains("length") || defSource.toLowerCase().contains("size") || defSource.toLowerCase().contains("max")) {
                            transformableNode.filePathCheck = true;
                            transformableNode.filePathCheckTag = "if检查了length/size/max";
                        }
                        if(defSource.toLowerCase().contains("startswith")) {
                            transformableNode.filePathCheck = true;
                            transformableNode.filePathCheckTag = "if检查了startsWith";
                        }
                        if(defSource.toLowerCase().contains("endswith")) {
                            transformableNode.filePathCheck = true;
                            transformableNode.filePathCheckTag = "if检查了endsWith";
                        }
                    }
                }
            }
        }

        if(transformableNode.filePathCheck) {
            dataFlowResult.hasFilePathCheck = true;
        }
    }

}
