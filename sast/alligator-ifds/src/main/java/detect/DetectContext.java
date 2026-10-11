package detect;

import TranModel.datastruct.PayloadStructure;
import app.context.SpringContextUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import detect.dataflow.DataFlowTrack;
import detect.knowledge.Knows;
import detect.summary.datastructure.DetectContextDependency;
import detect.vulexp.datastruct.SingleChainVulExpResult;
import intelligent.ConditionParser;
import intelligent.condition.CT;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.SootMethod;

import java.util.*;


@Slf4j
@Getter
@Setter
public class DetectContext {

    public int id = 0;
    @JsonIgnore
    private boolean fakeCallStack; // CHA算法可能产生假的callstack，设置这个field方便之后标记为假
    @JsonIgnore
    private SootMethod entry;
    @JsonIgnore
    private SootMethod sink;
    @JsonIgnore
    public List<SootMethod> callStack;
    @JsonIgnore
    public Map<String, SootMethod> callStackMap;
    @JsonIgnore
    private Knows knows;
    @JsonIgnore
    private DetectContextDependency detectContextDependency = new DetectContextDependency(this);
    @JsonIgnore
    public HashMap<String, ConditionParser.ParseResult> transferMapConditions = new HashMap<>();

    public boolean conditionNotSatisfy = false;
    public boolean conditionSatisfy = false;
    public String conditionNotSatisfyReason = "";
    public String conditionSatisfyReason = "";

    public HashMap<String, HashMap<Integer, CT>> method_argInd_to_CT = new HashMap<>();

    public List<PayloadStructure> payloadStructures;
    public DataFlowTrack.DataFlowResult dataFlowResult;
    public String vulType;

    private String entryMethod;
    private String sinkMethod;
    public String URL = null;
    public List<String> payloads = null;

    public List<String> reachableEntries;

    public List<String> callStackMethods;
    // 调用栈的String表达
    public String callStackToString;

    public SingleChainVulExpResult singleChainVulExpResult;

    public int getLLMExpRank() {
        if(singleChainVulExpResult != null) {
            return singleChainVulExpResult.LLMExpRank;
        }
        return -1;
    }

    public List<String> getCallStackMethods() {
        if (callStackMethods == null) {
            callStackMethods = new LinkedList<>();
            for(SootMethod sootMethod : callStack) {
                callStackMethods.add(sootMethod.toString());
            }
        }
        return callStackMethods;
    }
    public String getEntryMethod(){
        return entry.toString();
    }
    public String getSinkMethod(){
        return sink.toString();
    }

    public int getCallStackLength() {
        return callStack.size();
    }

    public String getVulType() {
        if (callStack == null) {
//            log.error("empty callstack");
            return "Unknown";
        }
        SootMethod sink = callStack.get(callStack.size() - 1);
        Map<String, List<SootMethod>> sootMethodlistMap =  knows.getSinkTypes();
//        log.error("size: " + sootMethodlistMap.entrySet().size());
        Iterator<Map.Entry<String, List<SootMethod>>> entries = sootMethodlistMap.entrySet().iterator();
        while (entries.hasNext()){
            Map.Entry<String, List<SootMethod>> entry = entries.next();
            if (entry.getValue().contains(sink)){
                return VulSinkTypeEnum.getValue(entry.getKey());
            }
        }
        return "Unknown";
    }

    public enum VulSinkTypeEnum {
        _SQL("sink_type_sql", "SQL Injection"),
        _NDAY("sink_type_nday", "N-day"),
        _RCE("sink_type_rce", "RCE"),
        _SSRF("sink_type_ssrf", "SSRF"),
        _F_RCE("RCE", "RCE"),
        _PATH_TRAVERSAL("sink_type_pathtraversal", "Path Traversal"),
        _XXE("sink_type_xxe", "XXE");

        private String sinkType;
        private String vulType;

        private VulSinkTypeEnum(String sinkType, String vulType) {
            this.sinkType = sinkType;
            this.vulType = vulType;
        }

        public static String getValue(String sinkType) {
            VulSinkTypeEnum[] enums = values();
            for (VulSinkTypeEnum enumTypes : enums) {
                if (enumTypes.sinkType().equals(sinkType)) {
                    return enumTypes.vulType();
                }
            }
            return sinkType;
        }


        private String sinkType() {
            return this.sinkType;
        }

        private String vulType() {
            return this.vulType;
        }
    }
}
