package detect.knowledge.constraint;

import TranModel.Symbol.Comparison;
import TranModel.Transformable;
import TranModel.datastruct.ConditionNode;
import basic.util.Pair;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import detect.dataflow.DataFlowTrack;
import soot.Scene;
import soot.SootMethod;

import java.util.HashMap;
import java.util.LinkedList;

public class EqualsConstraint extends Constraint{

    public EqualsConstraint() {
        constraintMethods = new LinkedList<>();
        constraintParams = new HashMap<>();

        try {
            SootMethod cons1 = Scene.v().getMethod("<java.lang.Object: boolean equals(java.lang.Object)>");
            for (SootMethod sootMethod: ClassUtils.getAllSubMethods(cons1.getDeclaringClass(),  cons1.getSubSignature())) {
                constraintMethods.add(sootMethod);
                constraintParams.put(sootMethod, new Pair<>(-1, 0));
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable) {
        ConditionNode conditionNode = super.recordConstraint(dataFlowResult, transformable);
        if (conditionNode == null)
            return conditionNode;

        conditionNode.comparison = Comparison.EQUAL;

        return conditionNode;
    }

    @Override
    public String getTypeName() {
        return "Equals";
    }
}
