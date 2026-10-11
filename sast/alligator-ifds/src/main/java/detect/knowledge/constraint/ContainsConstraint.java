package detect.knowledge.constraint;

import TranModel.Symbol.Comparison;
import TranModel.Transformable;
import TranModel.datastruct.ConditionNode;
import basic.util.Pair;
import detect.dataflow.DataFlowTrack;
import soot.Scene;
import soot.SootMethod;

import java.util.HashMap;
import java.util.LinkedList;

public class ContainsConstraint extends Constraint{
    public ContainsConstraint(){
        constraintMethods = new LinkedList<>();
        constraintParams = new HashMap<>();

        try {
            SootMethod cons1 = Scene.v().getMethod("<java.lang.String: boolean contains(java.lang.CharSequence)>");
            constraintMethods.add(cons1);
            constraintParams.put(cons1, new Pair<>(-1,0));

            SootMethod cons2 = Scene.v().getMethod("<java.util.Collection: boolean contains(java.lang.Object)>");
            constraintMethods.add(cons2);
            constraintParams.put(cons2, new Pair<>(-1, 0));

            SootMethod cons3 = Scene.v().getMethod("<java.util.Map: boolean containsKey(java.lang.Object)>");
            constraintMethods.add(cons3);
            constraintParams.put(cons3, new Pair<>(-1, 0));

            SootMethod cons4 = Scene.v().getMethod("<java.util.Map: boolean containsValue(java.lang.Object)>");
            constraintMethods.add(cons4);
            constraintParams.put(cons4, new Pair<>(-1, 0));
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable) {
        ConditionNode conditionNode = super.recordConstraint(dataFlowResult, transformable);
        if (conditionNode == null)
            return null;
        conditionNode.comparison = Comparison.HAS;

        // 修正比较符号
        return conditionNode;
    }

    @Override
    public String getTypeName() {
        return "contains_element";
    }
}
