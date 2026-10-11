package detect.vulexp;

import detect.DetectContext;
import detect.ExploitDetector;
import detect.vulexp.datastruct.BasicInfo;
import detect.vulexp.datastruct.SingleChainVulExpResult;
import detect.vulexp.datastruct.VulExpResult;
import intelligent.IntelligentResolverVulExp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.*;

@Component
@Slf4j
public class EXPChecker {

    @Autowired
    BasicInfo basicInfo;
    @Autowired
    IntelligentResolverVulExp intelligentResolverVulExp;

    public void rank(DetectContext detectContext) {

        detectContext.singleChainVulExpResult = new SingleChainVulExpResult();

        Map.Entry<Integer, SootMethod> entry = getDownStreamCallSiteMethod(detectContext.callStack);
        Map.Entry<Integer, SootMethod> entry2 = getUpstreamCalleeAPIMethodSignature(detectContext.callStack);
        if(entry == null && entry2 == null){
            log.warn("在调用栈上分上下游，都失败，退出");
            return;
        }
        SootMethod callSiteMethod = null;
        String riskyAPIMethodSig = null;
        if(entry != null && entry2 != null){
            int ind1 = entry.getKey();
            callSiteMethod = entry.getValue();
            int ind2 = entry2.getKey();
            riskyAPIMethodSig = entry2.getValue().getSignature();
            if(ind1 != ind2 - 1) {
                log.warn("在调用栈上分上下游，但是上下游不相邻，将上游设置为下游+1");
                riskyAPIMethodSig = detectContext.callStack.get(ind1 + 1).getSignature();
            }
            detectContext.singleChainVulExpResult.up_down_split = ind1 + " ; " + ind2;
        } else if(entry != null) {
            int ind1 = entry.getKey();
            callSiteMethod = entry.getValue();
            riskyAPIMethodSig = detectContext.callStack.get(ind1 + 1).getSignature();
            log.warn("在调用栈上分上下游，但是上游失败，将上游设置为下游+1");
            detectContext.singleChainVulExpResult.up_down_split = ind1 + " ; (" + (ind1+1) + ")";
        } else {
            // entry == null && entry2 != null
            int ind2 = entry2.getKey();
            riskyAPIMethodSig = entry2.getValue().getSignature();
            callSiteMethod = detectContext.callStack.get(ind2 - 1);
            log.warn("在调用栈上分上下游，但是下游失败，将下游设置为上游-1");
            detectContext.singleChainVulExpResult.up_down_split = "(" + (ind2-1) + ") ; " + ind2;
        }

        if(riskyAPIMethodSig == null || callSiteMethod == null) return;

        log.info("callstack: " + detectContext.callStack);
        log.info("callSiteMethod: " + callSiteMethod);
        log.info("riskyAPIMethodSig: " + riskyAPIMethodSig);

        log.warn("query LLM...");

        String reply = intelligentResolverVulExp.rankEXP(callSiteMethod, riskyAPIMethodSig, detectContext.callStackToString);
        // 使用换行符分割字符串
        String[] lines = reply.split("\\n", 2);

        if (lines.length >= 1) {
            String firstLine = lines[0];
            int rank = -1;
            try {
                rank = Integer.parseInt(firstLine.trim());
                log.warn("LLM为漏洞打分: " + rank);
            } catch (NumberFormatException e) {
                // 处理可能的数字格式异常
            }
            detectContext.singleChainVulExpResult.LLMExpRank = rank;
        }

        detectContext.singleChainVulExpResult.LLMExplain = reply;

    }

    /** 为整个CVE,upstream,downstream记录整体可利用性 */
    public void rankAllCUV(List<DetectContext> detectContexts, VulExpResult vulExpResult) {
        Collections.sort(detectContexts, Comparator.comparingInt(DetectContext::getLLMExpRank).reversed());

        // 取前三个对象
        List<DetectContext> topThree = detectContexts.subList(0, Math.min(3, detectContexts.size()));
        // 创建新的列表存放前三个对象
        vulExpResult.topExploitableCallChains = new LinkedList<>(topThree);

        if(!vulExpResult.topExploitableCallChains.isEmpty()) {
            vulExpResult.LLMExpRank = vulExpResult.topExploitableCallChains.get(0).getLLMExpRank();
        }
    }

    /** 获得调用链上downstream对risky API的call-site所在的方法 **/
    private Map.Entry<Integer, SootMethod> getDownStreamCallSiteMethod(List<SootMethod> callStack) {
        for (int i = callStack.size() - 1; i >= 0; i--) {
            SootMethod sootMethod = callStack.get(i);
            for (String pkg : basicInfo.downstreamPkgNames) {
                if (sootMethod.getDeclaringClass().getName().contains(pkg)) {
                    return new AbstractMap.SimpleEntry<>(i, sootMethod);
                }
            }
        }
        // 没找到返回null
        return null;
    }

    /** 获得调用链上upstream的risky API **/
    private Map.Entry<Integer, SootMethod> getUpstreamCalleeAPIMethodSignature(List<SootMethod> callStack) {
        SootMethod lastMethod = null;
        for (int i = callStack.size() - 1; i >= 0; i--) {
            SootMethod sootMethod = callStack.get(i);
            boolean inUpstream = false;
            for(String pkg : basicInfo.upstreamPkgNames) {
                if(sootMethod.getDeclaringClass().getName().contains(pkg)) {
                    inUpstream = true;
                }
            }
            if(!inUpstream && lastMethod != null) {
                return new AbstractMap.SimpleEntry<>(i+1, lastMethod);
            }
            lastMethod = sootMethod;
        }
        return null;
    }
}
