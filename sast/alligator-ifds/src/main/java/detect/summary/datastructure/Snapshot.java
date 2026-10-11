package detect.summary.datastructure;

import TranModel.TransformableNode;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import detect.DetectContext;
import detect.dataflow.DataFlowTrack;
import intelligent.condition.CT;
import soot.SootMethod;

import java.util.HashMap;
import java.util.List;

// DetectContext检测时的snapshot，用来在context间恢复之前没进行好的分析
public class Snapshot {
    public DetectContext detectContext;
    public int stack_ind; // 当前（待分析）的栈第几层
    public MethodDescriptor methodDescriptor; // 当前（待分析）的方法描述
    public DataFlowTrack dataFlowTrack;
    public HashMap<Integer, CT> value2CT = new HashMap<>();
    public List<Integer> taintedParams;
    public SootMethod invokeMethod;
    public TransformableNode invokeNode = null;
    public boolean hazardResult;

    public Snapshot(DetectContext detectContext, int stack_ind, MethodDescriptor methodDescriptor, DataFlowTrack dataFlowTrack, SootMethod invokeMethod) {
        this.detectContext = detectContext;
        this.stack_ind = stack_ind;
        this.methodDescriptor = methodDescriptor;
        this.dataFlowTrack = dataFlowTrack;
        this.invokeMethod = invokeMethod;
        MethodDescriptor invokeDescriptor = MethodDescriptor.getOrCreateDescriptor(invokeMethod);
        this.value2CT = (HashMap<Integer, CT>) invokeDescriptor.value2CT.clone();
    }
}
