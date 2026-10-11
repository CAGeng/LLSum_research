package intelligent.condition;

import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.Node;
import basic.dataflow.DataFlow;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.ValueBox;

import java.util.HashMap;
import java.util.List;

public class ConditionCollector {

    public void collectCT2(MethodDescriptor methodDescriptor, TransformableNode invokeNode, SootMethod invokeMethod, int argInd, ValueBox argValueBox, CT ct) {
        // Collecting CT2 value compare
        if(argValueBox.getValue().toString().equals("1")) {
            ct.CT2_enable = true;
            ct.CT2 = 1;
        }
        if(argValueBox.getValue().toString().equals("0")) {
            ct.CT2_enable = true;
            ct.CT2 = 0;
        }
        if(!ct.CT2_enable) {
            List<Node> defNodes = DataFlow.getDefNodes(argValueBox, invokeNode.node);
            for(Node defNode : defNodes) {
                List<ValueBox> defAndUseBoxes = defNode.unit.getUseAndDefBoxes();
                for(ValueBox defAndUseBox : defAndUseBoxes) {
                    HashMap<Integer, Value> paramIdMapLocalValue = methodDescriptor.getParamIdMapLocalValue();
                    Integer ind = null;
                    for(Integer key : paramIdMapLocalValue.keySet()) {
                        if(paramIdMapLocalValue.get(key).equals(defAndUseBox.getValue())) {
                            ind = key;
                            break;
                        }
                    }
                    // 此时上层的ind参数传递给下层的argValuebox
                    if(methodDescriptor.value2CT.containsKey(ind)) {
                        CT useCT = methodDescriptor.value2CT.get(ind);
                        if(useCT.CT2_enable) {
                            ct.CT2_enable = true;
                            ct.CT2 = useCT.CT2;
                            HashMap<SootMethod, Integer> callerCTMap = new HashMap<>();
                            callerCTMap.put(methodDescriptor.sootMethod, ind);
                            String calleeCTMapString = invokeMethod.getSignature() + ":" + argInd;
                            CT.CT_relatedMethodParamIndex.put(calleeCTMapString, callerCTMap);
                        }
                    }
                }
            }
        }
//        if(!ct.CT2_enable) {
//            // 这是启发式规则, 直接在同一个位置匹配
//            if(methodDescriptor.value2CT.containsKey(argInd)) {
//                CT useCT = methodDescriptor.value2CT.get(argInd);
//                if(useCT.CT2_enable) {
//                    ct.CT2_enable = true;
//                    ct.CT2 = useCT.CT2;
//                    ct.relatedMethodParamIndex.put(methodDescriptor.sootMethod, argInd);
//                }
//            }
//        }

    }

    public CT collectCT(MethodDescriptor methodDescriptor, TransformableNode invokeNode, SootMethod invokeMethod, int argInd, ValueBox argValueBox) {
        CT ct = new CT();
        List<Type> parameterTypes = invokeMethod.getParameterTypes();
        if(argInd >= parameterTypes.size()) {
            return ct;
        }
        if(argInd >= 0 && parameterTypes.get(argInd).toString().equals("boolean")) {
            collectCT2(methodDescriptor, invokeNode, invokeMethod, argInd, argValueBox, ct);
        }
        return ct;
    }
}
