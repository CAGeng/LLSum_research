package detect.knowledge.constraint;

import TranModel.Symbol.Comparison;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import detect.dataflow.DataFlowTrack;
import soot.Type;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;

public class ClassCastConstraint extends Constraint{

    @Override
    public ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable) {
        TransformableNode tfNode = (TransformableNode) transformable;
        if (!(tfNode.node.unit instanceof AssignStmt))
            return null;

        AssignStmt assignStmt = (AssignStmt) tfNode.node.unit;
        Value right = assignStmt.getRightOp();
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(tfNode.method);

        if (!(right instanceof CastExpr))
            return null;

        Type castType = ((CastExpr) right).getCastType();
        ConditionNode newConditionNode = new ConditionNode(tfNode,false);
        Value value = ((CastExpr) right).getOp();
        TaintNode taintNode = descriptor.getTaintNodeByValue(value);
        if (taintNode == null)
            return null;

        newConditionNode.values.add(taintNode.srcMessage);
        newConditionNode.constants.put(castType.toString(), castType.toString());
        newConditionNode.comparison = Comparison.EQUAL;

        dataFlowResult.conditionNodes.add(newConditionNode);
        return newConditionNode;
    }

    @Override
    public String getTypeName() {
        return "ClassCast";
    }
}
