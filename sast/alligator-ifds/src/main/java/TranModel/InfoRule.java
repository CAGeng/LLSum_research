package TranModel;

import detect.dataflow.DataFlowTrack;

public interface InfoRule {
    void apply(Transformable transformable, DataFlowTrack.DataFlowResult dataFlowResult);
}
