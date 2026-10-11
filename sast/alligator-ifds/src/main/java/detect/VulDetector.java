package detect;

import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.DefaultDetector.MethodSummary;
import basic.cg.CG;
import basic.cg.CGOptimizer;
import basic.util.LogUtil;
import basic.util.StaticAnalyzeUtils.InvokeStmtUtil;
import basic.util.StaticAnalyzeUtils.MethodUtil;
import detect.dataflow.DataFlowTrack;
import detect.knowledge.Knows;
import detect.knowledge.entry.EasyFromFileEntrySet;
import detect.knowledge.entry.ElasticSearchEntrySet;
import detect.knowledge.entry.FromXmlEntrySet;
import detect.knowledge.entry.discovery.AnnotationEntrySet;
import detect.knowledge.entry.discovery.ArgDiscoverEntrySet;
import detect.knowledge.entry.jetty.RequestTypeEntrySet;
import detect.knowledge.entry.spring.ServiceEntrySet;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.knowledge.entry.tomcat.ConnectorEntrySet;
import detect.knowledge.entry.tomcat.ContainerEntrySet;
import detect.knowledge.entry.tomcat.RequestEntrySet;
import detect.knowledge.sink.ConditionalSinks;
import detect.knowledge.sink.SinkDetector;
import detect.summary.CGSegmenter;
import detect.summary.datastructure.OverAllHazards;
import detect.summary.datastructure.Snapshot;
import detect.unit.instrument.InstrumentUtil;
import detect.unit.instrument.Instruments;
import intelligent.ConditionParser;
import intelligent.IntelligentLogger;
import intelligent.IntelligentResolver;
import intelligent.SourceCodeUtil;
import intelligent.condition.CT;
import intelligent.datacollect.MethodData;
import intelligent.datacollect.TaintFactData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import soot.*;
import sourceprocess.FileUtils;
import sourceprocess.JavaFileParser;
import sourceprocess.MethodInfo;
import sourceprocess.MethodInfoService;

import javax.annotation.PreDestroy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Component
@Scope("prototype")
@Slf4j
public class VulDetector{

    private CG cg;
    private List<DetectContext> detectContexts = new LinkedList<>();

    @Autowired
    SpringEntrySet springEntrySet;

    @Autowired
    GeneralConfig generalConfig;

    public boolean only_testcase_result = false;
    public boolean backup_discover_entry = false; // 开启后，会从sink倒着查找entry
    public boolean filter_too_long_stack = true; // 开启后，不要太长的调用栈
    public boolean processSrc = false;
    public boolean optimizeEntry = true; // entry去冗余，把被调用的entry去掉，需要在构建cg后进行，较heavy

    public boolean multiPathDetectSwitcher = false; // 调用图构建完后，多条调用路径的，是否多路径检测

    public HashMap<String, List<DetectContext>> detect() {

        SourceCodeUtil.srcRootDir = generalConfig.srcDir;
        if(processSrc) {
            processSrc(generalConfig.srcDir);
        }

        Knows knows = SpringContextUtils.getBean(Knows.class);
        List<SootMethod> entries1 = new LinkedList<>();

        List<SootMethod> entries1_1 = knows.getSpringEntrySet().forceRefreshEntries();
        List<SootMethod> entries1_2 = new ServiceEntrySet().forceRefreshEntries();
        List<SootMethod> entries2 = knows.getServletEntrySet().forceRefreshEntries();
        ElasticSearchEntrySet elasticSearchEntrySet = SpringContextUtils.getBean(ElasticSearchEntrySet.class);
        List<SootMethod> entries3 = elasticSearchEntrySet.forceRefreshEntries();
        ConnectorEntrySet connectorEntrySet = new ConnectorEntrySet();
        List<SootMethod> entries4 = connectorEntrySet.forceRefreshEntries();
        ContainerEntrySet containerEntrySet = new ContainerEntrySet();
        List<SootMethod> entries5 = containerEntrySet.forceRefreshEntries();
        RequestEntrySet requestEntrySet = new RequestEntrySet(); // 启发式规则字符串
        List<SootMethod> entries5_1 = requestEntrySet.forceRefreshEntries();
        EasyFromFileEntrySet easyFromFileEntrySet = new EasyFromFileEntrySet();
        easyFromFileEntrySet.inputFilePath = "/Users/fortsun/PycharmProjects/pythonProject/scripts/entries.txt";
        List<SootMethod> entries6 = new LinkedList<>();
        // 检查文件存在
        Path path = Paths.get(easyFromFileEntrySet.inputFilePath);
        if(!Files.exists(path)) {
            log.error("entries文件不存在,不指定特定entries: " + easyFromFileEntrySet.inputFilePath);
        } else {
            entries6 = easyFromFileEntrySet.forceRefreshEntries();
            System.out.println("从文件中指定的entry: " + entries6.size());
        }

//        log.warn("指定了所有public api为entry");
        List<SootMethod> entries7 = new LinkedList<>();
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            if (sootClass.isPublic()) {
                for (SootMethod sootMethod : sootClass.getMethods()) {
                    if (sootMethod.isPublic()) {
                        entries7.add(sootMethod);
                    }
                }
            }
        }

        // jetty
        // 带参数的指定entry
        RequestTypeEntrySet requestTypeEntrySet = new RequestTypeEntrySet();
        List<SootMethod> entries8 = requestTypeEntrySet.forceRefreshEntries();
        knows.methodInputMap.putAll(requestTypeEntrySet.methodInputMap);
        FromXmlEntrySet fromXmlEntrySet = new FromXmlEntrySet();
        List<SootMethod> entries9 = fromXmlEntrySet.forceRefreshEntries();
        knows.methodInputMap.putAll(fromXmlEntrySet.methodInputMap);

        // 从communication基本类型发现继承
        ArgDiscoverEntrySet argDiscoverEntrySet = new ArgDiscoverEntrySet();
        List<SootMethod> entries10 = argDiscoverEntrySet.forceRefreshEntries();

        AnnotationEntrySet annotationEntrySet = SpringContextUtils.getBean(AnnotationEntrySet.class);
        List<SootMethod> entries11 = annotationEntrySet.forceRefreshEntries();

        entries1.addAll(entries1_1);
//        entries1.addAll(entries1_2);
        entries1.addAll(entries2);
//        entries1.addAll(entries3);
        entries1.addAll(entries4); // connector
        entries1.addAll(entries5); // container
        entries1.addAll(entries5_1); // request arg名称启发式规则
//        entries1.addAll(entries6);
//        entries1.addAll(entries7); // PUBLIC 方法all
//        entries1.addAll(entries8);
//        entries1.addAll(entries9);
        entries1.addAll(entries10); // arg discover
        entries1.addAll(entries11); // annotation

        System.out.println("从spring注解中发现的entry: " + entries1_1.size());
//        System.out.println("从spring ServiceImpl中发现的entry: " + entries1_2.size());
        System.out.println("从衍生注解中发现的entry: " + entries11.size());
        System.out.println("从servlet注解中发现的entry: " + entries2.size());
        System.out.println("从容器结构中发现的entry: " + (entries4.size() + entries5.size()));
        System.out.println("从参数类型中发现的entry: " + entries5_1.size());
        System.out.println("从衍生的参数类型中发现的entry: " + entries10.size());

        knows.setEntries(entries1);


//        List<SootMethod> testEntries = new LinkedList<>();
//        SootClass myClass2 = Scene.v().getSootClass("com.l4yn3.microserviceseclab.controller.PointToTest.PointToAnalysisTest");
//        for(SootMethod sootMethod : myClass2.getMethods()) {
//            String sootMethodName = sootMethod.getName();
//            if (sootMethodName.contains("entry1")){
//                testEntries.add(sootMethod);
//            }
//        }
//        log.error("!!! 用测试entry替换本来的entry了 ！！！");
////        int a = 1/0;
//        knows.setEntries(testEntries);

        List<SootMethod> entries = knows.getEntries();
        if (generalConfig.chainTrackMode) {
            log.error("chain track mode： 只跟踪指定的entry");
            entries = new LinkedList<>();
            for (SootClass sootClass : Scene.v().getApplicationClasses()) {
                if (sootClass.getName().contains(generalConfig.entryClassName)) {
                    for (SootMethod sootMethod : sootClass.getMethods()) {
                        if (sootMethod.getName().equals(generalConfig.entryMethodName)) {
                            entries.add(sootMethod);
                        }
                    }
                }
            }
        }

        String outBasicPath = generalConfig.sootOutputPath;
        log.info("entry count: " + entries.size());
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(
                    Paths.get(outBasicPath).resolve("entries.txt").toString()
            ));
            for (SootMethod sootMethod : entries) {
                writer.write(sootMethod.toString());
                writer.newLine();
            }
            writer.close();
        } catch (Exception ignored) {
        }


        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
        for (SootMethod sootMethod : entries) {
            intelligentLogger.logFile3(sootMethod.getSignature());
        }

        if (generalConfig.sparkSwitcher) {
            log.warn("using spark");
            CG.setSpark(true);
        }
        cg = new CG(entries);

        if(optimizeEntry) {
            HashSet<SootMethod> optimizedEntries = new HashSet<>();
            for(SootMethod sootMethod : entries) {
                if(cg.edgesInto(sootMethod).isEmpty()) {
                    optimizedEntries.add(sootMethod);
                }
            }
            log.error("优化后（去冗余）的entry数量: " + optimizedEntries.size());
            entries = new LinkedList<>(optimizedEntries);
            try {
                BufferedWriter writer = new BufferedWriter(new FileWriter(
                        Paths.get(outBasicPath).resolve("entries_opt.txt").toString()
                ));
                for (SootMethod sootMethod : optimizedEntries) {
                    writer.write(sootMethod.toString());
                    writer.newLine();
                }
                writer.close();
            } catch (Exception ignored) {
            }

        }

        System.exit(0);

        MethodData.methodCallSiteInCG(cg);
        MethodData.collectLibCalls(cg);

//        log.error("(((((((");
////        SootMethod targetMethod = null;
//        for(SootClass sootClass : Scene.v().getClasses()) {
//            if(sootClass.getName().contains("InternalTemplateManager")) {
//                for(SootMethod sootMethod : sootClass.getMethods()) {
//                    if(sootMethod.getName().equals("getClassloaderTemplate")) {
//                        log.error(sootMethod.getSignature());
//                        System.out.println(sootMethod.getActiveBody().toString());
//                    }
//                }
//            }
//        }
//        int a = 1/0;
//        log.error(")))))))");

        HashSet<SootMethod> allReachableMethodsToTarget1 = cg.getAllReachableMethodFromEntry();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("missed-method.txt"));
            for (SootMethod sootMethod : allReachableMethodsToTarget1) {
                if (!sootMethod.hasActiveBody()) {
                    writer.write(sootMethod.toString());
                    writer.newLine();
                }
            }
            writer.close();
            System.out.println("Code has been written to missed-method.txt successfully.");
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
//
//        log.error("(((((((");
//        for(SootClass sootClass : Scene.v().getClasses()) {
//            for(SootMethod sootMethod : sootClass.getMethods()) {
//                if(sootMethod.getName().equals("naturelikeSortilegy")) {
//                    log.error(sootMethod.getSignature());
//
//                }
//            }
//        }
//        log.error(")))))))");

        knows.expandKnowsAfterCG();
        Set<SootMethod> sinks = knows.getSinks();

        // ai-test: 增加test sink
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            if (sootClass.getName().contains("IndexController") ||
                    sootClass.getName().contains("WhpController") ||
                    sootClass.getName().contains("com.l4yn3.microserviceseclab.controller.common.Dataflow")) {
                for (SootMethod sootMethod : sootClass.getMethods()) {
                    System.out.println(sootMethod);
                    // <com.l4yn3.microserviceseclab.controller.IndexController: void sink(java.lang.String)>
                    if (sootMethod.getName().equals("sink")) {
                        sinks.add(sootMethod);
                    }
                }
            }
        }

        ConditionalSinks conditionalSinks = SpringContextUtils.getBean(ConditionalSinks.class);
        for (String m : conditionalSinks.conditionalSinks) {
            try {
                SootMethod sootMethod = Scene.v().getMethod(m);
                if (sootMethod != null) {
                    sinks.add(sootMethod);
                }
            } catch (Exception e) {
                // miss method
            }
        }

        List<String> additionalSinks = generalConfig.getGeneralList().additionalSinks;
        for (String mm : additionalSinks) {
            try {
                System.out.println("add sink: " + mm);
                SootMethod sootMethod = Scene.v().getMethod(mm);
                if (sootMethod != null) {
                    System.out.println("successful add sink: " + sootMethod.getSignature());
                    sinks.add(sootMethod);
                }
            } catch (Exception e) {
//                e.printStackTrace();
            }
        }


        if (generalConfig.chainTrackMode) {
            log.error("chain track mode： 只跟踪指定的sink");
            sinks = new HashSet<>();
            for (SootClass sootClass : Scene.v().getApplicationClasses()) {
                if (sootClass.getName().contains(generalConfig.sinkClassName)) {
                    for (SootMethod sootMethod : sootClass.getMethods()) {
                        if (sootMethod.getName().equals(generalConfig.sinkMethodName)) {
                            sinks.add(sootMethod);
                        }
                    }
                }
            }
        }

        for (SootMethod sootMethod : sinks) {
            intelligentLogger.logFileSink(sootMethod.getSignature());
        }

        log.info("entry count: " + entries.size());
        log.info("sink count: " + sinks.size());

        CGOptimizer.targetMethods = new LinkedList<>(sinks);
        CGOptimizer.cg = cg;
        CGOptimizer.getInstance().optimizeCG();

        Instruments instruments = new Instruments();

        int stack_con = 0;
        int fakeCallStack = 0;
        int detectContextCon = 0;

        List<String> allCallStack = new ArrayList<>();
        HashMap<String, DetectContext> callStackToContext = new HashMap<>();

        // 寻找sink wrapper, 这里只做日志打印，不直接使用
        SinkDetector sinkDetector = SpringContextUtils.getBean(SinkDetector.class);
        sinkDetector.cg = cg;
        sinkDetector.detectSinks();
        for(SootMethod sootMethod : sinkDetector.discoveredSinks) {
            System.out.println("添加sink: " + sootMethod.getSignature());
            sinks.add(sootMethod);
        }

        System.out.println("sinkDetector检测sink后：");
        log.info("sink count: " + sinks.size());

        for (SootMethod sinkMethod : sinks) {
            HashMap<SootMethod, SootMethod> methodMapNext = new HashMap<>();
            HashMap<SootMethod, Integer> method2NextSize = new HashMap<>(); // 记录method到下一跳的数量
            HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(sinkMethod, methodMapNext, method2NextSize);
            for (SootMethod sootMethod : allReachableMethodsToTarget) {
                if(sootMethod.equals(sinkMethod)) {
                    continue;
                }
                if (entries.contains(sootMethod) ||
                        (backup_discover_entry && sootMethod.isPublic() && sootMethod.getDeclaringClass().isPublic()) && !sootMethod.getSignature().startsWith("<java")) {
//                    log.info("entry: " + sootMethod);
//                    log.info("sink: " + sinkMethod);
//                    log.info("---call stack---");

                    List<SootMethod> callStack = new LinkedList<>();
                    SootMethod it = sootMethod;
                    while (it != null) {
                        callStack.add(it);
//                        log.debug(it.toString());
                        it = methodMapNext.get(it);
                    }

                    // 简单过滤下CHA可能产生的虚假调用栈
                    if (!CG.isSpark()) {
                        boolean fake = false;
                        for (SootMethod method : callStack) {
                            if (CG.mayBeFakeCallByCHA(method)) {
                                fake = true;
                            }
                        }
                        if (fake) {
                            fakeCallStack += 1;
                            continue;
                        }
                    }

                    stack_con++;

                    if (filter_too_long_stack && callStack.size() > 5) continue;

                    // 这里我需要判断：对于entry到sink有多条路径的，我需要将这些路径都记录下来
                    boolean multiPath = false;
                    for(int i = 0; i < callStack.size(); i++) {
                        SootMethod method = callStack.get(i);
                        if (method2NextSize.containsKey(method) && method2NextSize.get(method) > 1) {
                            multiPath = true;
                        }
                    }

                    if(!multiPath || !multiPathDetectSwitcher) {
                        // 普通的添加
                        DetectContext detectContext = new DetectContext();
                        detectContextCon += 1;
                        detectContext.id = detectContextCon;
                        detectContext.setEntry(sootMethod);
                        detectContext.setSink(sinkMethod);
                        detectContext.setCallStack(callStack);
                        detectContext.setKnows(knows);
                        detectContexts.add(detectContext);
                    } else {
                        System.out.println("多条路径检索: " + sootMethod);
                        List<List<SootMethod>> allCallPaths = MethodUtil.getAllCallPaths(sootMethod, sinkMethod, cg, 5);
                        System.out.println("多条路径检索结果: " + allCallPaths.size());
                        for (List<SootMethod> callPath : allCallPaths) {
                            DetectContext detectContext = new DetectContext();
                            detectContextCon += 1;
                            detectContext.id = detectContextCon;
                            detectContext.setEntry(sootMethod);
                            detectContext.setSink(sinkMethod);
                            detectContext.setCallStack(callPath);
                            detectContext.setKnows(knows);
                            detectContexts.add(detectContext);
                        }
                    }
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
            for (SootMethod sootMethod : callStackMethods) {
                if (sinks.contains(sootMethod)) {
                    if (!detectContext.getSink().equals(sootMethod)) {
                        iterator.remove();
                        break;
                    }
                }
            }
        }

        log.error("去重后调用链总数: " + detectContexts.size());
        if (detectContexts.size() > 2000) {

            log.warn("链太多，重新去重...");
//            if(!CG.isSpark()) {
//                log.warn("重新用spark构建CG...");
//                CG.setSpark(true);
//                cg = new CG(entries);
//            }

            HashSet<SootMethod> visitedSinks = new HashSet<>(); // 去重时，确保每个sink，如果有链，至少保留一条；这么做为了方便后续的约束提取
            for (SootMethod sinkMethod : sinks) {
                HashMap<SootMethod, SootMethod> methodMapNext = new HashMap<>();
                HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(sinkMethod, methodMapNext);

                for (SootMethod sootMethod : allReachableMethodsToTarget) {
                    if (entries.contains(sootMethod)) {
//                    log.info("entry: " + sootMethod);
//                    log.info("sink: " + sinkMethod);
//                    log.info("---call stack---");
                        List<SootMethod> callStack = new LinkedList<>();
                        // callStackToString记录String类型的调用栈
                        List<String> callStackToString = new ArrayList<>();
                        SootMethod it = sootMethod;
                        while (it != null) {
                            callStack.add(it);
                            callStackToString.add(it.getSignature());
//                        log.debug(it.toString());
                            it = methodMapNext.get(it);
                        }
                        SootMethod lastMethod = callStack.get(callStack.size() - 1);
                        // 保留最小集合
                        String newString = callStackToString.toString().substring(1, callStackToString.toString().length() - 1);
                        boolean flag = true;
                        // 用于保证只添加一次
                        boolean hasAdd = false;
                        if (allCallStack.isEmpty()) {
                            allCallStack.add(newString);
                            visitedSinks.add(lastMethod);
                            hasAdd = true;
                        } else {
                            List<String> temp = new ArrayList<>(allCallStack);
                            // 只在重复sink时候比较
                            if (visitedSinks.contains(lastMethod)) {
                                for (String singleCallStack : temp) {
                                    // 相同的调用链或非最简调用链，直接下一个
                                    if (newString.contains(singleCallStack)) {
                                        flag = false;
                                        break;
                                    }
                                    // 说明newString是比singleCallStack更精简的调用链
                                    if (singleCallStack.contains(newString)) {
                                        if (!hasAdd) {
                                            allCallStack.add(newString);
                                            visitedSinks.add(lastMethod);
                                            hasAdd = true;
                                        }

                                        // 只有在它们的sink一样时才删掉之前的，防止这个sink不再出现
                                        String[] split = singleCallStack.split(", ");
                                        String lastMethod2 = split[split.length - 1];
                                        if (lastMethod2.equals(lastMethod.toString())) {
                                            // 保存newString, 剔除singleCallStack
                                            allCallStack.remove(singleCallStack);
                                            // 同时，删除map表中，singleCallStack对应的context
                                            callStackToContext.remove(singleCallStack);
                                        }
                                    }
                                }
                            }
                        }

                        // flag=true表示要增加
                        if (!flag) {
                            continue;
                        }

                        if (!hasAdd) {
                            allCallStack.add(newString);
                            visitedSinks.add(lastMethod);
                        }

                        // 简单过滤下CHA可能产生的虚假调用栈
                        if (!CG.isSpark()) {
                            boolean fake = false;
                            for (SootMethod method : callStack) {
                                if (CG.mayBeFakeCallByCHA(method)) {
                                    fake = true;
                                }
                            }
                            if (fake) {
                                fakeCallStack += 1;
                                continue;
                            }
                        }

                        if (!callStackToContext.containsKey(newString)) {
                            DetectContext detectContext = new DetectContext();
                            detectContext.setEntry(sootMethod);
                            detectContext.setSink(sinkMethod);
                            detectContext.setCallStack(callStack);
                            detectContext.setCallStackToString(newString);
                            detectContext.setKnows(knows);
                            // 保存到map中
                            callStackToContext.put(newString, detectContext);
                        }
                    }
                }
            }
            log.error("调用链总数: " + allCallStack.size());
            log.error("去重后调用链总数: " + callStackToContext.size());
            log.error("重新设置detectcontexts集合...");
            // 从map中拿出所有的context
            detectContexts = new ArrayList<>();
            for (String callStack : callStackToContext.keySet()) {
                detectContexts.add(callStackToContext.get(callStack));
            }
        }

        List<MethodSummary> methodSummaries = new LinkedList<>();

        List<DetectContext> sortedDetectContext = detectContexts;
        if(DataFlowTrack.hazardOpen) {
            CGSegmenter cgSegmenter = SpringContextUtils.getBean(CGSegmenter.class);
            for (DetectContext detectContext : detectContexts) {
                cgSegmenter.id2DetectContext.put(detectContext.id, detectContext);
            }
            sortedDetectContext = cgSegmenter.segment(cg);
        } else {
            log.warn("没有开启hazard模式，跳过cg segment");
        }


        int detect_con = 0;
        OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
        for (DetectContext detectContext : sortedDetectContext) {
            if (generalConfig.summaryLevel < 3) MethodDescriptor.flushAllDescriptors();
            List<SootMethod> riskyCallStack = detectContext.callStack;
            DataFlowTrack dataFlowTrack = new DataFlowTrack(riskyCallStack, cg, detectContext);
            overAllHazards.detectContext2DataFlowTrack.put(detectContext, dataFlowTrack);

            // 数据流分析
            DataFlowTrack.DataFlowResult dataFlowResult = dataFlowTrack.trackDataflow();
            detectContext.dataFlowResult = dataFlowResult;
            InstrumentUtil.recordIfStmtAndMethodForInst(dataFlowTrack, instruments);

            detect_con += 1;
            log.warn("检测完成: " + detect_con + "/" + detectContexts.size());
        }

        // 对hazard的detectContext进行修正
        log.warn("有多少hazard的detectContext需要修正 " + overAllHazards.needUpdateContext.size());
        int refine_con = 0;
        for (DetectContext detectContext : overAllHazards.needUpdateContext) {
            DataFlowTrack dataFlowTrack = overAllHazards.detectContext2DataFlowTrack.get(detectContext);
            // 数据流分析
            dataFlowTrack.hazard_mode = 4;
            DataFlowTrack.DataFlowResult dataFlowResult = dataFlowTrack.trackDataflow();
            detectContext.dataFlowResult = dataFlowResult;

            refine_con += 1;
            log.warn("hazard的detectContext修正完成 " + refine_con + "/" + overAllHazards.needUpdateContext.size());
        }

        for (SootMethod sootMethod : MethodDescriptor.methodMapDescriptor.keySet()) {
            MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
            if (!methodDescriptor.methodSummary.tags.isEmpty()) {
                // 说明有摘要
//                if(!methodDescriptor.methodSummary.getConditionalTaint().isEmpty() &&
//                    !methodDescriptor.methodSummary.getConditionalTaint().keySet().iterator().next().equals("[]")) { //只打印有输入污点的函数
//                    methodSummaries.add(methodDescriptor.methodSummary);
//                    HashSet<String> explains = TaintFactData.dataflowFactInMethod(methodDescriptor);
//                    methodDescriptor.methodSummary.dataflowExplanation = String.join("\n", explains);
//                }
                methodSummaries.add(methodDescriptor.methodSummary);
                HashSet<String> explains = TaintFactData.dataflowFactInMethod(methodDescriptor);
                methodDescriptor.methodSummary.dataflowExplanation = String.join("\n", explains);
            } else {
//                System.out.println("无摘要: " + sootMethod);
                if (DataFlowTrack.branchCallMethods.contains(sootMethod)) {
                    System.out.println("可是他在旁路里！");
                }
            }
        }
        System.out.println("摘要复用次数: " + DataFlowTrack.con_summaryReuse);
        LogUtil.outputToJSON(methodSummaries, outBasicPath, "All-summaries.json");
        log.warn("摘要总数: " + methodSummaries.size());
        intelligentLogger.log("LLM产生函数摘要个数： " + intelligentLogger.conLLMSummary);
        intelligentLogger.log("LLM产生函数摘要失败数： " + intelligentLogger.conLLMSummaryFailed);
        intelligentLogger.log("涉及多个incoming的摘要个数: " + overAllHazards.allIncomingDiffMethodCount);


        // 对所有LLMSum的methods的context进行condition的检查
        IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
        for(SootMethod sootMethod : intelligentResolver.LLMSumMethod2result.keySet()) {
            processConditionInEverySnapshot(sootMethod, null, null);
        }

        int notSatisfy = 0, check = 0;
        for(DetectContext detectContext : detectContexts) {
            if(detectContext.conditionNotSatisfy) {
                notSatisfy += 1;
            }
            if(detectContext.dataFlowResult.hasBoundaryCheck || detectContext.dataFlowResult.hasFilePathCheck) {
                check += 1;
            }
        }

        int satisfy = 0;
        for(DetectContext detectContext : detectContexts) {
            if(detectContext.conditionSatisfy) {
                satisfy += 1;
            }
        }

        log.warn("有条件满足的链路：" + hasConditionMatchContexts.size());
        log.warn("有条件和摘要比不满足的链路：" + hasConditionNotMatchContexts.size());
        log.warn("所有有条件不满足的链路数（和摘要比，和路径约束比),(经过检查)：" + notSatisfy + " , " + check);
        log.warn("所有有条件满足的链路数（和摘要比，和路径约束比）：" + satisfy);


        // 补充URL信息
        for (DetectContext detectContext : detectContexts) {
            if (springEntrySet.entryMapURL.containsKey(detectContext.getEntry())) {
                detectContext.URL = springEntrySet.entryMapURL.get(detectContext.getEntry());
            }
            if (springEntrySet.entryMapPayloadKeys.containsKey(detectContext.getEntry())) {
                detectContext.payloads = springEntrySet.entryMapPayloadKeys.get(detectContext.getEntry());
            }
            if (springEntrySet.entryMapPayloadStructures.containsKey(detectContext.getEntry())) {
                detectContext.payloadStructures = springEntrySet.entryMapPayloadStructures.get(detectContext.getEntry());
            }
            if(generalConfig.output_all_apis) {
                // 在这里补充一个从上层的CG上的可达性分析
                SootMethod entry = detectContext.getEntry();
                HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(entry);
                List<String> allEntries = new LinkedList<>();
                for(SootMethod sootMethod : allReachableMethodsToTarget) {
                    allEntries.add(sootMethod.getSignature());
                }
                detectContext.reachableEntries = allEntries;
            }
        }


        // map: 漏洞类型 -> 检测结果列表
        HashMap<String, List<DetectContext>> jsonResultObject = new HashMap<>();
        if (only_testcase_result) {
            // ai-test只要人工定义的source()的测试结果
            for (DetectContext detectContext : detectContexts) {
//                if(!detectContext.dataFlowResult.isHasDataFlow()) continue;
                String vulType = detectContext.getVulType();
                if (!(vulType.equals("Unknown"))) continue;
                if (!jsonResultObject.containsKey(vulType)) {
                    jsonResultObject.put(vulType, new LinkedList<>());
                }
                jsonResultObject.get(vulType).add(detectContext);
            }
        } else {
            int hasDataFlow = 0, call_stack_total = 0;
            int deletedDummySink = 0;
            for (DetectContext detectContext : detectContexts) {
                // dummy sink, 没有数据流不显示，防止过多
                if(SinkDetector.DUMMY_CLASS_NAME.contains(detectContext.getSink().getDeclaringClass().getName()) && !detectContext.dataFlowResult.isHasDataFlow()) {
                    deletedDummySink += 1;
                    continue;
                }
                if (detectContext.isFakeCallStack()) {
                    fakeCallStack += 1;
                    continue;
                }
                call_stack_total += 1;
                SootMethod entry = detectContext.getEntry();
                intelligentLogger.logUsedEntry(entry.toString());
                if (detectContext.dataFlowResult.isHasDataFlow()) hasDataFlow += 1;
                String vulType = detectContext.getVulType();
                if (!jsonResultObject.containsKey(vulType)) {
                    jsonResultObject.put(vulType, new LinkedList<>());
                }
                jsonResultObject.get(vulType).add(detectContext);
            }
            log.warn("CHA产生的假调用栈: " + fakeCallStack);
            log.error("共计调用链： " + call_stack_total);
            log.error("有数据流： " + hasDataFlow);

            log.error("删除的dummy sink(没有数据流不显示，防止过多): " + deletedDummySink + "/" + detectContexts.size());
        }

        LogUtil.outputToJSON(jsonResultObject, outBasicPath, generalConfig.sootOutputFilename);
        // 记录插桩信息
        InstrumentUtil.recordSink(knows, instruments);
        LogUtil.outputToJSON(instruments, outBasicPath, generalConfig.getSootOutputInsFileName);

        SpringContextUtils.destroyBean(knows);

        // 把配置和输出记录在输出目录
        // 项目根目录的文件路径
        File sourceFile1 = new File("./console-out.log");
        String destinationPath1 = outBasicPath + "/console-out.log";
        copyFile(sourceFile1, destinationPath1);

        // Spring Boot 资源目录的文件路径
        File sourceFile2 = new File("./alligator-ifds/src/main/resources/application.yml");
        String destinationPath2 = outBasicPath + "/application.yml";
        copyFile(sourceFile2, destinationPath2);

        return jsonResultObject;
    }

    public void processSrc(String srcDir) {
        MethodInfoService methodInfoService = SpringContextUtils.getBean(MethodInfoService.class);
        JavaFileParser parser = new JavaFileParser();

        List<Path> javaFiles = null;
        try {
            javaFiles = FileUtils.findAllJavaFiles(Paths.get(srcDir));
        } catch (IOException e) {
            log.error("查找Java文件失败：" + e.getMessage());
        }
        for (Path file : javaFiles) {
            List<MethodInfo> methods = null;
            try {
                methods = parser.parse(file);
            } catch (IOException e) {
                System.out.println("解析文件失败：" + file);
            }
            methodInfoService.saveMethods(methods);
        }
    }


    private static void copyFile(File sourceFile, String destinationPath) {
        if (sourceFile.exists()) {
            try {
                // 复制文件
                Files.copy(sourceFile.toPath(), Paths.get(destinationPath), StandardCopyOption.REPLACE_EXISTING);
                System.out.println("文件已成功复制到目标路径：" + destinationPath);
            } catch (IOException e) {
                System.err.println("文件复制失败：" + e.getMessage());
            }
        } else {
            System.out.println("源文件不存在：" + sourceFile);
        }
    }

    @PreDestroy
    @ConditionalOnWebApplication
    public void preDestroy(){
        log.info("Destroy VulDetector when it's a WebApplication");
    }

    HashSet<DetectContext> hasConditionMatchContexts = new HashSet<>();
    HashSet<DetectContext> hasConditionNotMatchContexts = new HashSet<>();
    HashSet<Snapshot> finishedSnap = new HashSet<>();
    public void processConditionInEverySnapshot(SootMethod sootMethod, Integer targetArgInd, CT targetSummaryCT) {

        if(!(targetArgInd == null)) {
            System.out.println("处理嵌套传递的条件");
        }

        // 第一层第二\三个参数为空，之后非空，指定要匹配的参数和CT

        OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
        List<Snapshot> snapshots = overAllHazards.method2Snapshot.get(sootMethod);

        for (Snapshot snapshot : snapshots) {
            if(snapshot.invokeMethod == null || !snapshot.invokeMethod.equals(sootMethod)) continue;

            if(finishedSnap.contains(snapshot)) continue;
            finishedSnap.add(snapshot);

            MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
            System.out.println("snapshot: " + snapshot.detectContext.getEntry().getName());
            // 处理条件
            // 对于没有指定summaryCT的，遍历全部
            if(targetArgInd == null) {
                HashMap<String, ConditionParser.ParseResult> transferMapCondition = descriptor.methodSummary.transferMapCondition;
                for(Map.Entry<String, ConditionParser.ParseResult> entry : transferMapCondition.entrySet()) {
                    String transfer = entry.getKey(); // c0->r-1
                    // 首先看这个transfer是否重要, 就看这个method里被污染到的参数是否与这个transfer相关
                    // 这个transfer包含c0表示入参，r0表示出参，如果所有ci的i都没有在这个snapshot的传入里，那这个传播的条件不重要
                    boolean important = false;
                    for(int i = -1; i < descriptor.sootMethod.getParameterCount(); i++) {
                        if(transfer.contains("c" + i)) {
                            if(snapshot.taintedParams.contains(i)) {
                                important = true;
                                break;
                            }
                        }
                    }
                    // 再看如果所有ri的i，如果有在污点列表里，也算重要
                    for(int i = -1; i < descriptor.sootMethod.getParameterCount(); i++) {
                        if(transfer.contains("r" + i)) {
                            Unit unit = snapshot.invokeNode.node.unit;
                            List<ValueBox> allArgValueBoxes = InvokeStmtUtil.getAllArgValueBoxes(unit);
                            ValueBox objectValueBox = InvokeStmtUtil.getObjectValueBox(unit);
                            if(i == -1) {
                                if(objectValueBox != null && snapshot.methodDescriptor.getTaintNodeByValue(objectValueBox.getValue()) != null) {
                                    important = true;
                                    break;
                                }
                            } else {
                                if(allArgValueBoxes.size() > i) {
                                    Value value = allArgValueBoxes.get(i).getValue();
                                    if(snapshot.methodDescriptor.getTaintNodeByValue(value) != null) {
                                        important = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    // 包括return值
                    if(transfer.contains("r-2")) {
                        Value returnValue = InvokeStmtUtil.getReturnValue(snapshot.invokeNode.node.unit);
                        if(snapshot.methodDescriptor.getTaintNodeByValue(returnValue) != null) {
                            important = true;
                        }
                    }
                    if(!important) continue;


                    // 再看这个condition是否满足
                    CT summaryCT = null;
                    ConditionParser.ParseResult parseResult = entry.getValue();
                    try {
                        summaryCT = ConditionParser.convertToCT(parseResult);
                    } catch (Exception e) {
                        System.out.println("fail to parse condition: " + parseResult);
                    }

                    if(summaryCT != null) {
                        HashMap<Integer, CT> value2CT = snapshot.value2CT;
                        // 匹配value2CT中和parseResult相关的CT
                        int paramInd = parseResult.getParamIndex();
                        if(value2CT.containsKey(paramInd)) {
                            CT ct = value2CT.get(paramInd);
                            if(!ct.isEnabled()) {
                                // do Nothing
                            }
                            else if(ct.satisfy(summaryCT)) {
                                // 满足条件
                                System.out.println("满足条件 " + parseResult);
                                snapshot.detectContext.conditionSatisfy = true;
                                snapshot.detectContext.conditionSatisfyReason += "满足条件 " + parseResult + "\n";
                                hasConditionMatchContexts.add(snapshot.detectContext);
                            } else {
                                // 不满足条件
                                System.out.println("不满足条件 " + parseResult);
                                snapshot.detectContext.conditionNotSatisfy = true;
                                snapshot.detectContext.conditionNotSatisfyReason += "不满足条件 " + parseResult + "\n";
                                hasConditionNotMatchContexts.add(snapshot.detectContext);
                            }

                            // 对于有更（浅层）依赖的条件，我们需要处理更浅的snapshot
                            String calleeCTMapStr = snapshot.invokeMethod.getSignature() + ":" + paramInd;
                            if(CT.CT_relatedMethodParamIndex.containsKey(calleeCTMapStr)) {
                                for(Map.Entry<SootMethod, Integer> entry1 : CT.CT_relatedMethodParamIndex.get(calleeCTMapStr).entrySet()) {
                                    processConditionInEverySnapshot(entry1.getKey(), entry1.getValue(), summaryCT);
                                }
                            }

                            if(snapshot.detectContext.conditionNotSatisfyReason.equals("") && snapshot.detectContext.conditionSatisfyReason.equals("")) {
                                if(snapshot.taintedParams.contains(paramInd)){
                                    System.out.println("满足条件 " + parseResult);
                                    snapshot.detectContext.conditionSatisfy = true;
                                    snapshot.detectContext.conditionSatisfyReason += "满足条件(条件可控） " + parseResult + "\n";
                                    hasConditionMatchContexts.add(snapshot.detectContext);
                                } else {
                                    System.out.println("不满足条件 " + parseResult);
                                    snapshot.detectContext.conditionNotSatisfy = true;
                                    snapshot.detectContext.conditionNotSatisfyReason += "不满足条件（条件不可控） " + parseResult + "\n";
                                    hasConditionNotMatchContexts.add(snapshot.detectContext);
                                }
                            }
                        }
                    }
                }

            } else {
                // 对于指定了summaryCT的，只处理这个CT
                HashMap<Integer, CT> value2CT = snapshot.value2CT;
                // 匹配value2CT中和parseResult相关的CT
                int paramInd = targetArgInd;
                if(value2CT.containsKey(paramInd)) {
                    CT ct = value2CT.get(paramInd);
                    String condStr = "arg-" + targetArgInd + " == " + targetSummaryCT.toString();
                    if(!ct.isEnabled()) {
                        // do Nothing
                    }
                    else if(ct.satisfy(targetSummaryCT)) {
                        // 满足条件
                        System.out.println("满足条件 " + condStr);
                        snapshot.detectContext.conditionSatisfy = true;
                        snapshot.detectContext.conditionSatisfyReason += "满足条件 " + condStr + "\n";
                    } else {
                        // 不满足条件
                        System.out.println("不满足条件 " + condStr);
                        snapshot.detectContext.conditionNotSatisfy = true;
                        snapshot.detectContext.conditionNotSatisfyReason += "不满足条件 " + condStr + "\n";
                    }

                    // 对于有更（浅层）依赖的条件，我们需要处理更浅的snapshot
                    String calleeCTMapStr = snapshot.invokeMethod.getSignature() + ":" + paramInd;
                    if(CT.CT_relatedMethodParamIndex.containsKey(calleeCTMapStr)) {
                        for(Map.Entry<SootMethod, Integer> entry1 : CT.CT_relatedMethodParamIndex.get(calleeCTMapStr).entrySet()) {
                            processConditionInEverySnapshot(entry1.getKey(), entry1.getValue(), targetSummaryCT);
                        }
                    }

                    if(snapshot.detectContext.conditionNotSatisfyReason.equals("") && snapshot.detectContext.conditionSatisfyReason.equals("")) {
                        if(snapshot.taintedParams.contains(paramInd)){
                            System.out.println("满足条件 " + condStr);
                            snapshot.detectContext.conditionSatisfy = true;
                            snapshot.detectContext.conditionSatisfyReason += "满足条件(条件可控） " + condStr + "\n";
                        } else {
                            System.out.println("不满足条件 " + condStr);
                            snapshot.detectContext.conditionNotSatisfy = true;
                            snapshot.detectContext.conditionNotSatisfyReason += "不满足条件（条件不可控） " + condStr + "\n";
                        }
                    }
                }
            }
        }

    }

}
