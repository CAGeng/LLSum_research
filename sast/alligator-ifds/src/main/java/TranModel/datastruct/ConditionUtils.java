package TranModel.datastruct;

import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.Node;
import basic.util.Pair;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import basic.util.StaticAnalyzeUtils.Parameter;
import intelligent.condition.CT;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;

import java.util.HashMap;
import java.util.HashSet;

// TODO: 改成规则配置文件形式读入
public class ConditionUtils {
    public static HashMap<String, Pair<Integer, Integer>> compareMethodsMapInputArg = new HashMap<>();
    public static HashSet<String> sizeMethods = new HashSet<>();
    public static HashSet<String> mapElementSigs = new HashSet<>();
    public static void init(){
        compareMethodsMapInputArg.put("<java.lang.String: boolean equals(java.lang.Object)>", new Pair<>(-1,0)); // compare-compared
        sizeMethods.addAll(ClassUtils.getAllSubMethodSigs("<java.util.Collection: int size()>"));
        mapElementSigs.addAll(ClassUtils.getAllSubMethodSigs("<java.util.Map: java.lang.Object get(java.lang.Object)>"));
        mapElementSigs.addAll(ClassUtils.getAllSubMethodSigs("<java.util.Map: boolean containsKey(java.lang.Object)>"));
        mapElementSigs.addAll(ClassUtils.getAllSubMethodSigs("<java.util.Map: boolean containsValue(java.lang.Object)>"));
        mapElementSigs.addAll(ClassUtils.getAllSubMethodSigs("<java.util.Set: boolean contains(java.lang.Object)>"));
    }

    public static boolean findControllableSourceDirect(ConditionNode conditionNode,
                                                       MethodDescriptor descriptor,
                                                       Value value){
        boolean flag = false;
        TaintNode taintNode = descriptor.getTaintNodeByValue(value);
        if (taintNode != null){
            conditionNode.values.add(taintNode.srcMessage);
            if(taintNode.paramInd != null) {
                conditionNode.paramInds.add(taintNode.paramInd);
            }
            flag = true;
        }

        return flag;
    }

    public static void parseSources(ConditionNode conditionNode, HashSet<Node> sources,
                                    Value value, MethodDescriptor descriptor) {
        for (Node node: sources){
            conditionNode.defSources.add(node.unit.toString());

            // 入参
            if(node.unit.toString().contains("@parameter")) {
                for(ValueBox valueBox : node.unit.getDefBoxes()) {
                    Value definedParam = valueBox.getValue();
                    for(int ind : descriptor.getParamIdMapLocalValue().keySet()) {
                        if (descriptor.getParamIdMapLocalValue().get(ind).equals(definedParam)) {
                            conditionNode.paramInds.add(ind);
                            if(descriptor.value2CT.containsKey(ind)) {
                                CT ct = descriptor.value2CT.get(ind);
                                if(ct.isEnabled()) {
                                    conditionNode.relatedCT = ct;
                                }

                            }
                        }
                    }
                }
            }

            if (node.unit instanceof AssignStmt){
                AssignStmt assignStmt = (AssignStmt)node.unit;
                Value left = assignStmt.getLeftOp();
                Value right = assignStmt.getRightOp();
                // TODO：加入对表达式子的解析
                if (right instanceof Constant){
                    if (conditionNode.reverse)
                        conditionNode.flipComparison();
                    conditionNode.constants.put(right.getType().toString(), right.toString());
                }
                // TODO:如果既不是入参也不是field，那么可能是静态程序分析产生了误报，可以直接删除此种情况

                if (right instanceof LengthExpr){
                    Value op = ((LengthExpr)right).getOp();
                    TaintNode taintNode = descriptor.getTaintNodeByValue(op);
                    if (taintNode != null)
                        conditionNode.values.add(taintNode.srcMessage);
                }

                if (right instanceof InvokeExpr){
                    SootMethod invokedMethod = ((InvokeExpr)right).getMethod();
                   if (sizeMethods.contains(invokedMethod.getSignature())){
                        ValueBox thisValueBox = Parameter.getThisValueBox(node);
                        if (thisValueBox != null){
                            Value thisValue = thisValueBox.getValue();
                            TaintNode thisTaintNode = descriptor.getTaintNodeByValue(thisValue);
                            if (thisTaintNode != null){
                                conditionNode.values.add(thisTaintNode.srcMessage);
                            }
                        }
                    }
                }

                // 对类型表达式的解析处理, E.g. r0 instanceof java.util.Map
                if (right instanceof InstanceOfExpr){
                    conditionNode.flipComparison();
                    conditionNode.deleteLast = true;
                    findControllableSourceDirect(conditionNode, descriptor,((InstanceOfExpr)right).getOp());
                    Type instanceType = ((InstanceOfExpr)right).getCheckType();
                    conditionNode.constants.put(instanceType.getClass().toString(), instanceType.toString());
                }
            }
        }
    }
}
