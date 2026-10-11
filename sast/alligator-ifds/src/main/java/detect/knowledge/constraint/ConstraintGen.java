package detect.knowledge.constraint;

import TranModel.Transformable;
import TranModel.datastruct.ConditionNode;
import detect.dataflow.DataFlowTrack;

public interface ConstraintGen {
    ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable);
    String getTypeName();
}
