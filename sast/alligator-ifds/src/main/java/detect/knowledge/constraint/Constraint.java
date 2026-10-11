package detect.knowledge.constraint;

import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.util.Pair;
import basic.util.StaticAnalyzeUtils.Parameter;
import detect.dataflow.DataFlowTrack;
import soot.Scene;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.Constant;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class Constraint implements ConstraintGen{
    protected List<SootMethod> constraintMethods = new ArrayList<>();
    protected HashMap<SootMethod, Pair<Integer, Integer>> constraintParams = new HashMap<>();

    public ConditionNode recordConstraint(DataFlowTrack.DataFlowResult dataFlowResult, Transformable transformable){
        TransformableNode tfNode = (TransformableNode) transformable;
        if (!tfNode.containsInvoke())
            return null;

        InvokeExpr invokeExpr = ((Stmt) tfNode.node.unit).getInvokeExpr();
        SootMethod invokedMethod = invokeExpr.getMethod();
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(tfNode.method);
        if (!constraintMethods.contains(invokedMethod))
            return null;

        ConditionNode newConditionNode = new ConditionNode(tfNode, false);
        Pair<Integer, Integer> pair = constraintParams.get(invokedMethod);
        ValueBox arg1Box = Parameter.getArgValueBox(tfNode.node, pair.getKey());
        ValueBox arg2Box = Parameter.getArgValueBox(tfNode.node, pair.getValue());
        if (arg1Box != null && arg2Box != null){
            Value arg1 = arg1Box.getValue();
            Value arg2 = arg2Box.getValue();

            if (arg1 instanceof Constant){
                newConditionNode.constants.put(arg1.getType().toString(), arg1.toString());
            }else {
                TaintNode taintNode = descriptor.getTaintNodeByValue(arg1);
                if (taintNode != null)
                    newConditionNode.values.add(taintNode.srcMessage);
            }
            if (arg2 instanceof Constant){
                newConditionNode.constants.put(arg2.getType().toString(), arg2.toString());
            }else {
                TaintNode taintNode = descriptor.getTaintNodeByValue(arg2);
                if (taintNode != null)
                    newConditionNode.values.add(taintNode.srcMessage);
            }
        }

        dataFlowResult.conditionNodes.add(newConditionNode);

        return newConditionNode;
    }

    public boolean addMethodAndParam(String methodSig, int param1, int param2) {
        try {
            SootMethod sootMethod = Scene.v().getMethod(methodSig);
            constraintMethods.add(sootMethod);
            constraintParams.put(sootMethod, new Pair<>(param1,param2));
            return true;
        } catch (Exception e){
            // 没找到这个方法
            return false;
        }
    }
}
