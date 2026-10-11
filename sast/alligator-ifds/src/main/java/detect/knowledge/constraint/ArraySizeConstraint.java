package detect.knowledge.constraint;

import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import detect.dataflow.DataFlowTrack;

public class ArraySizeConstraint extends Constraint {
    @Override
    public ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable) {
        TransformableNode tfNode = (TransformableNode) transformable;
        ConditionNode conditionNode = super.recordConstraint(dataFlowResult, tfNode);

        return conditionNode;
    }

    @Override
    public String getTypeName() {
        return "Element_Array_Size";
    }
}
