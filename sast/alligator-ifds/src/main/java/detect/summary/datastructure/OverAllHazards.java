package detect.summary.datastructure;

import detect.DetectContext;
import detect.dataflow.DataFlowTrack;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

// 记录所有detectContext的hazard情况
@Component
public class OverAllHazards {
    public HashMap<SootMethod, List<Snapshot>> method2Snapshot = new HashMap<>(); // 方法到detectContext可以从snapshot里读
    public HashSet<DetectContext> allHazardedContext = new HashSet<>();

    // 这些是后续有更新摘要，需要进入最后一步修正的
    public HashSet<DetectContext> needUpdateContext = new HashSet<>();
    public HashMap<DetectContext, DataFlowTrack> detectContext2DataFlowTrack = new HashMap<>(); // 记录一下以在修正时通过context找到之前的dataflowTrack

    public int allIncomingDiffMethodCount = 0; // 所有具有incoming diff method的数量
}
