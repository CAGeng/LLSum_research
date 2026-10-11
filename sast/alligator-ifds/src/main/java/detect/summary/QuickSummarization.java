package detect.summary;

import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.dataflow.DataFlow;
import basic.dataflow.Event;
import basic.util.StaticAnalyzeUtils.Parameter;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

// 适用于对不复杂的方法快速summary以实现semantic inline
public class QuickSummarization {

    public HashMap<Integer, List<Integer>> summarize(SootMethod sootMethod) {
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        HashMap<Integer, List<Event>> parametersMapTaintedEvent = Parameter.getParametersMapTaintedEvent(cfg, sootMethod);
        HashMap<Integer, Value> parametersLocalValues = Parameter.getParametersLocalValues(cfg);

        HashMap<Integer, List<Integer>> taintMap = new HashMap<>();
        // 找每个参数taint的event，然后看这个event是不是其它param的ref或者是ret，如果是，就把这个param的event加到这个param的summary里
        // 先处理所有从node到param的反向映射
        HashMap<Value, Integer> nodeToParamInd = new HashMap<>();
        for(int ind : parametersLocalValues.keySet()) {
            nodeToParamInd.put(parametersLocalValues.get(ind), ind);
        }

        for(int ind : parametersMapTaintedEvent.keySet()) {
            List<Event> taintedEvents = parametersMapTaintedEvent.get(ind);
            for(Event taintedEvent : taintedEvents) {
                if(taintedEvent.node.toString().startsWith("return ")) {
                    taintMap.computeIfAbsent(ind, k -> new LinkedList<>()).add(-2);
                }
                if(nodeToParamInd.containsKey(taintedEvent.valueBox.getValue())) {
                    taintMap.computeIfAbsent(ind, k -> new LinkedList<>()).add(nodeToParamInd.get(taintedEvent.valueBox.getValue()));
                }
            }
        }

        return taintMap;
    }
}
