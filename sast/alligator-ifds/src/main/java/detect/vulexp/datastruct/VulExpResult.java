package detect.vulexp.datastruct;

import detect.DetectContext;

import java.util.LinkedList;
import java.util.List;

public class VulExpResult {
    public boolean cgReachable = false;
    public boolean dataReachable = false;
    public int LLMExpRank = -1;

    public List<DetectContext> topExploitableCallChains = new LinkedList<>();

}
