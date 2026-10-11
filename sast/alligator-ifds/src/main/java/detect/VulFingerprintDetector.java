package detect;

import TranModel.datastruct.TaintNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.cg.CG;
import basic.cg.CGOptimizer;
import basic.util.LogUtil;
import basic.util.Pair;
import detect.dataflow.DataFlowTrack;
import detect.knowledge.Knows;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.unit.instrument.InstrumentUtil;
import detect.unit.instrument.Instruments;
import detect.vulexp.datastruct.RiskyFuncInfo;
import detect.vulexp.SearchFuncUtil;
import detect.vulexp.relation.RelationProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import javax.annotation.PreDestroy;
import java.util.*;

@Component
@Scope("prototype")
@Slf4j
public class VulFingerprintDetector {

    private CG cg;
    private List<DetectContext> detectContexts = new LinkedList<>();

    @Autowired
    SpringEntrySet springEntrySet;

    @Autowired
    GeneralConfig generalConfig;

    public boolean only_testcase_result = false;

    public List<RiskyFuncInfo> riskyFuncInfos = new LinkedList<>();

    public List<Pair<String, Integer>> detect() {
//    public HashMap<String, List<DetectContext>> detect() {

        Knows knows = SpringContextUtils.getBean(Knows.class);

        List<SootMethod> allPublicMethods = new LinkedList<>();
        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if(sootMethod.isPublic()) {
                    allPublicMethods.add(sootMethod);
                }
            }
        }
        CG.setSpark(true);
        cg = new CG(allPublicMethods);

        knows.expandKnowsAfterCG();

        //找sink
        Set<SootMethod> sinks = new HashSet<>();
        for(RiskyFuncInfo riskyFuncInfo : riskyFuncInfos) {
            List<SootMethod> sootMethods = SearchFuncUtil.searchRiskyFuncs(riskyFuncInfo);
            sinks.addAll(sootMethods);
        }

        // 找调用sink的public api
        List<SootMethod> entries = new LinkedList<>();
        for(SootMethod sink : sinks) {
            HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(sink);
            for(SootMethod sootMethod : allReachableMethodsToTarget) {
                if(sootMethod.isPublic() && sootMethod.getDeclaringClass().isPublic()) {
                    entries.add(sootMethod);
                }
            }
        }
        knows.setEntries(entries);


        log.info("entry count: " + entries.size());
        log.info("entry: " + entries);
        log.info("sink count: " + sinks.size());

        CGOptimizer.targetMethods = new LinkedList<>(sinks);
        CGOptimizer.cg = cg;
        CGOptimizer.getInstance().optimizeCG();

        log.info("打印sink之间的relation...");
        RelationProcessor relationProcessor = SpringContextUtils.getBean(RelationProcessor.class);
        relationProcessor.processCall(new HashSet<>(sinks), cg);

        Instruments instruments = new Instruments();

        int stack_con = 0;
        int detectContextCon = 0;
        for(SootMethod sinkMethod : sinks) {
            HashMap<SootMethod, SootMethod> methodMapNext = new HashMap<>();
            HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(sinkMethod, methodMapNext);
            for(SootMethod sootMethod : allReachableMethodsToTarget) {
                if(entries.contains(sootMethod)) {
                    stack_con ++;
//                    log.info("entry: " + sootMethod);
//                    log.info("sink: " + sinkMethod);
//                    log.info("---call stack---");
                    List<SootMethod> callStack = new LinkedList<>();
                    SootMethod it = sootMethod;
                    while(it != null) {
                        callStack.add(it);
//                        log.debug(it.toString());
                        it = methodMapNext.get(it);
                    }
                    DetectContext detectContext = new DetectContext();
                    detectContextCon += 1;
                    detectContext.id = detectContextCon;
                    detectContext.setEntry(sootMethod);
                    detectContext.setSink(sinkMethod);
                    detectContext.setCallStack(callStack);
                    detectContext.setKnows(knows);
                    detectContexts.add(detectContext);
                }

            }
        }
        log.error("调用链总数: " + stack_con);

//        System.exit(0);

        // 遍历所有的调用链，如果调用链中包含sink，但是sink不是最后一个方法，那么这个调用链是重复的，要删除
        Iterator<DetectContext> iterator = detectContexts.iterator();
        while (iterator.hasNext()) {
            DetectContext detectContext = iterator.next();
            List<SootMethod> callStackMethods = detectContext.callStack;
            for(SootMethod sootMethod : callStackMethods) {
                if (sinks.contains(sootMethod)) {
                    if (!detectContext.getSink().equals(sootMethod)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        log.error("去重后调用链总数: " + detectContexts.size());

        int detect_con = 0;
        for(DetectContext detectContext : detectContexts) {
            if(generalConfig.summaryLevel < 3) MethodDescriptor.flushAllDescriptors();
            List<SootMethod> riskyCallStack = detectContext.callStack;
            DataFlowTrack dataFlowTrack = new DataFlowTrack(riskyCallStack, cg, detectContext);

            // 数据流分析
            DataFlowTrack.DataFlowResult dataFlowResult = dataFlowTrack.trackDataflow();
            detectContext.dataFlowResult = dataFlowResult;
            InstrumentUtil.recordIfStmtAndMethodForInst(dataFlowTrack, instruments);

            // 记录分支约束的数据关系
            relationProcessor.processData(detectContext);

            detect_con += 1;
            log.warn("检测完成: " + detect_con + "/" + detectContexts.size());
        }

        relationProcessor.onFinishDetectContext(new HashSet<>(sinks), cg);


        // 补充URL信息
        for(DetectContext detectContext : detectContexts){
            if(springEntrySet.entryMapURL.containsKey(detectContext.getEntry())) {
                detectContext.URL = springEntrySet.entryMapURL.get(detectContext.getEntry());
            }
            if(springEntrySet.entryMapPayloadKeys.containsKey(detectContext.getEntry())) {
                detectContext.payloads = springEntrySet.entryMapPayloadKeys.get(detectContext.getEntry());
            }
            if(springEntrySet.entryMapPayloadStructures.containsKey(detectContext.getEntry())) {
                detectContext.payloadStructures = springEntrySet.entryMapPayloadStructures.get(detectContext.getEntry());
            }
        }


        // map: 漏洞类型 -> 检测结果列表
        HashMap<String, List<DetectContext>> jsonResultObject = new HashMap<>();
        int hasDataFlow = 0;
        for(DetectContext detectContext : detectContexts) {
//                if(!detectContext.dataFlowResult.isHasDataFlow()) continue;
            hasDataFlow += 1;
            String vulType = detectContext.getVulType();
            if(!jsonResultObject.containsKey(vulType)) {
                jsonResultObject.put(vulType, new LinkedList<>());
            }
            jsonResultObject.get(vulType).add(detectContext);
        }
        log.error("共计调用链： " + detectContexts.size());
        log.error("有数据流： " + hasDataFlow);

        List<Pair<String, Integer>> apiSources = new LinkedList<>();
        for(DetectContext detectContext : detectContexts){
            if(detectContext.dataFlowResult.isHasDataFlow()) {
                List<List<TaintNode>> dataPaths = detectContext.dataFlowResult.getDataPaths();
                if(dataPaths == null) continue;
                for(List<TaintNode> dataPath : dataPaths) {
                    TaintNode srcNode = dataPath.get(0);
                    SootMethod apiMethod = srcNode.inMethod;
                    Integer srcParam = srcNode.paramInd;
                    apiSources.add(new Pair<>(apiMethod.getSignature(), srcParam));
                }
            }
        }

        LogUtil.outputToJSON(apiSources, generalConfig.sootOutputPath, "riskyAPIs.json");


        LogUtil.outputToJSON(jsonResultObject, generalConfig.sootOutputPath, generalConfig.sootOutputFilename);
        // 记录插桩信息
        InstrumentUtil.recordSink(knows, instruments);
        LogUtil.outputToJSON(instruments, generalConfig.sootOutputPath, generalConfig.getSootOutputInsFileName);

        SpringContextUtils.destroyBean(knows);

//        return jsonResultObject;
        return apiSources;
    }



    @PreDestroy
    @ConditionalOnWebApplication
    public void preDestroy(){
        log.info("Destroy VulDetector when it's a WebApplication");
    }

}
