package detect.dataflow;

import TranModel.Rule;
import TranModel.Rules.*;
import TranModel.Symbol.Comparison;
import TranModel.TranUtil;
import TranModel.TransformableNode;
import TranModel.algorithm.Dijkstra;
import TranModel.datastruct.ConditionNode;
import TranModel.datastruct.PayloadStructure;
import TranModel.datastruct.SrcMessage;
import TranModel.datastruct.TaintNode;
import TranModel.state.StateUtil;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import app.utils.Constants;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.cg.CG;
import basic.cg.CGOptimizer;
import basic.dataflow.DataFlow;
import basic.dataflow.fieldsensitive.AccessPathNode;
import basic.util.LogUtil;
import basic.util.StaticAnalyzeUtils.Parameter;
import basic.util.TimeOutTask;
import basic.util.Util;
import com.fasterxml.jackson.annotation.JsonIgnore;
import detect.DetectContext;
import detect.knowledge.Knows;
import detect.knowledge.constraint.ClassCastConstraint;
import detect.knowledge.constraint.ContainsConstraint;
import detect.knowledge.constraint.EqualsConstraint;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.knowledge.entry.webapp.ServletEntrySet;
import detect.knowledge.sink.ConditionalSinks;
import detect.knowledge.sink.SinkDetector;
import detect.summary.Differentiation;
import detect.summary.datastructure.DetectContextDependency;
import detect.summary.datastructure.OverAllHazards;
import detect.summary.datastructure.Snapshot;
import detect.unit.instrument.UsedSiteRecord;
import intelligent.IntelligentLogger;
import intelligent.IntelligentResolver;
import intelligent.condition.CT;
import intelligent.condition.ConditionCollector;
import intelligent.datacollect.TaintFactData;
import intelligent.predict.ComplexityCalculate;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/*
    数据流追踪： 一条从downstream方法到upstream方法的调用栈
 */
@Slf4j
public class DataFlowTrack {

    public List<SootMethod> callStack;
    public List<TaintNode> srcs;
    public List<TaintNode> sinks = new LinkedList<>();
    public TaintSanitizer taintSanitizer = new TaintSanitizer();
    public DataFlowResult dataFlowResult;

    public boolean taint_sanitize_open = SpringContextUtils.getBean(GeneralConfig.class).taintSanitizeSwitcher;
    public CG cg;

    public boolean simple_dataPath = SpringContextUtils.getBean(GeneralConfig.class).simpleDataPathSwitcher;
    public boolean only_dataflow_callstack_interprocedural = SpringContextUtils.getBean(GeneralConfig.class).onlyDataflowCallStackInter;
    public boolean summaryReuse = true;
    public boolean timeoutInterprocedural = false;
    public long LLMSummary_ComplexityThreshold = 500;
    public boolean waterFallMode = false; // 瀑布模式下从entry开始广泛展开调用栈，用于sink无法提前确定的情形
    public int waterFallDepth = 2; // 展开深度
    public static boolean hazardOpen = false;
    List<SootMethod> hazardMethods = new LinkedList<>(); // 这个标记将来要hazard的方法
    List<SootMethod> indeedHazardMethods = new LinkedList<>(); // 这个记录分析中实际hazard了的方法
    HashMap<SootMethod, Snapshot> hazardM2Snapshot = new HashMap<>(); // 这个记录hazard方法对应的snapshot

    Differentiation differentiation = new Differentiation();

    public HashSet<SootMethod> detectedMethods = new HashSet<>(); // 这个dataflowtrack检测过的方法

    public DetectContext detectContext;

    public DataFlowTrack(List<SootMethod> stack) {
        callStack = stack;
        assert callStack.size() > 0;
        cg = new CG(callStack.get(0));
    }

    public DataFlowTrack(List<SootMethod> stack, CG cg) {
        callStack = stack;
        assert callStack.size() > 0;
        this.cg = cg;
    }

    public DataFlowTrack(List<SootMethod> stack, CG cg, DetectContext detectContext) {
        callStack = stack;
        assert callStack.size() > 0;
        this.cg = cg;
        this.detectContext = detectContext;
    }

    public DataFlowResult trackDataflow() {

        if(waterFallMode) log.error("WaterFallMode is on, the dataflow track will start from the entry method as a waterfall");

        TranUtil.init(cg);
        ComplexityCalculate.cg = cg;
        TransformableNode.clearRules();
        TransformableNode.clearInfoRules();
        TransformableNode.clearConstraintGen();
        TransformableNode.addRule(new JoinRule()); // 这个必须第一个加入
        TransformableNode.addRule(new TaintNodeRule());
        TransformableNode.addRule(new TestcaseSourceRule());
        TransformableNode.addRule(new RecordLLMExplainRule());
        TransformableNode.addRule(new AliasTypeRule());
        TransformableNode.addInfoRule(new ConditionRule());
        TransformableNode.addConstraintGen(new ClassCastConstraint());
        TransformableNode.addConstraintGen(new ContainsConstraint());
        TransformableNode.addConstraintGen(new EqualsConstraint());

        // 标记hazard方法
        LinkedList<DetectContextDependency.Relationship> analysisRiskyChain = detectContext.getDetectContextDependency().analysisRiskyChain;
        for(DetectContextDependency.Relationship relationship : analysisRiskyChain) {
            hazardMethods.add(relationship.bridgeMethod);
        }
        log.warn("这个detectContext下需要hazard的方法个数: " + hazardMethods.size());

        RuleCollection ruleCollection = SpringContextUtils.getBean(RuleCollection.class);
        Map<String, Rule> ruleMap = ruleCollection.getRuleMap();
        Iterator<Map.Entry<String, Rule>> entries = ruleMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Rule> entry = entries.next();
            Rule rule = entry.getValue();
            TransformableNode.addRule(rule);
        }

        // 在每次数据透传分析前，刷新MethodDescriptor的缓存
        GeneralConfig generalConfig = SpringContextUtils.getBean(GeneralConfig.class);
        if (generalConfig.summaryLevel < 3) MethodDescriptor.flushAllDescriptors();

        // 注入Src
        findAndInjectSrces();

        this.dataFlowResult = new DataFlowResult();

        dataFlowResult.setSrcs(srcs);

        // ai-test: 每次刷一遍testcaseSource规则中可能通过source = new Source()产生的source
        TestcaseSourceRule.testSources = new LinkedList<>();

        // System.out.println("DEBUG: " + DEBUG_METHOD);
        boolean dataReach = trackDataFlow_recurse();

        if (IntelligentResolver.ai_predict_record) {
            IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
            log.error("预测复杂度区间计数");
            LogUtil.printBox("预测复杂度区间计数");
            intelligentResolver.complexityProcessor.print();
            LogUtil.printBox("预测复杂度分析真实时间（纳秒）");
            intelligentResolver.complexityProcessor.printTime();
        }

        dataFlowResult.setHasDataFlow(dataReach);
        dataFlowResult.setSinks(sinks);

        // ai-test: 每次刷一遍testcaseSource规则中可能通过source = new Source()产生的source
        List<TaintNode> testSources = TestcaseSourceRule.testSources;
        srcs.addAll(testSources);

        if (dataReach) {
            String callChain = callStack.stream()
                    .map(element -> element.getName())
                    .collect(Collectors.joining(" -> "));
            log.warn("有数据透传 " + callChain);
//            log.warn("Sink: {");
//            for(TaintNode sink : sinks) {
//                log.warn("  " + sink);
//            }
//            log.warn("}");

            for (TaintNode sink : sinks) {
                for (TaintNode src : srcs) {
                    Dijkstra dijkstra = new Dijkstra(src, sink);
                    boolean found = dijkstra.run();
                    if (found) {
                        List<TaintNode> path = dijkstra.getSrcTargetPath();
//                        log.warn("一条数据通路，sink： " + sink);
                        List<TaintNode> dataPathInParam = new LinkedList<>();
                        for (TaintNode taintNode : path) {
                            if (simple_dataPath && taintNode.paramInd == null) continue; // 只看方法参数taint path
//                            log.warn("   " + taintNode);
                            dataPathInParam.add(taintNode);

                        }
                        dataFlowResult.addDataPath(dataPathInParam);
                        break;
                    }
                }
            }
        } else {
            String callChain = callStack.stream()
                    .map(element -> element.getName())
                    .collect(Collectors.joining(" -> "));
            log.warn("无数据透传 " + callChain);
            log.warn("数据传播到 " + maxTaintSpread + " 层调用栈");
            SootMethod method = callStack.get(maxTaintSpread);
            log.info("数据传播到的最后一层方法： " + method);

            // 数据流没有跟完，也简单过滤下CHA可能产生的虚假调用栈
            for (SootMethod sootMethod : callStack) {
                if (CG.mayBeFakeCallByCHA(sootMethod)) {
                    detectContext.setFakeCallStack(true);
                }
            }
        }

        log.warn("实际hazard的方法个数: " + hazardM2Snapshot.size());
        return dataFlowResult;
    }

    public String DEBUG_METHOD = "<com.xpn.xwiki.internal.template.InternalTemplateManager: org.xwiki.template.Template getClassloaderTemplate(java.lang.ClassLoader,java.lang.String,java.lang.String)>";

    public int hazard_mode = 0; // 0: 普通模式 1: risky模式 4: 修增模式
    public int track_ind = 0; // 记录track到的调用栈深度
    public int maxTaintSpread = 0; // 记录最大数据传播深度

    private boolean trackDataFlow_recurse() {

        log.error("trackDataFlow_recurse: " + callStack.get(track_ind).getSignature());

        boolean retValue = false;
        int remember_track_ind = track_ind;

        if (track_ind == callStack.size() - 1) {
            SootMethod sootMethod = callStack.get(track_ind);

            if (taint_sanitize_open) taintSanitizer.sanitizeStringPayload(sootMethod);
            return catchSinks(sootMethod);
        }

        SootMethod sootMethod = callStack.get(track_ind);
        SootMethod nextMethod = callStack.get(track_ind + 1);

        boolean DEBUG = sootMethod.getSignature().equals(DEBUG_METHOD);
        detectedMethods.add(sootMethod);


        // 污点清除
        if (taint_sanitize_open) taintSanitizer.sanitizeStringPayload(sootMethod);

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        List<Integer> taintedParams1 = descriptor.getTaintedParams();
        if(taintedParams1 != null) {
            maxTaintSpread = Math.max(maxTaintSpread, track_ind);
        }

        StringBuilder llmExplainStr = new StringBuilder();
        if(TransformableNode.recordExplain) {
            // 记录初始状态
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams != null) {
                llmExplainStr.append("方法名：" + sootMethod.getName());
                llmExplainStr.append("方法签名：" + sootMethod.getSignature());
                llmExplainStr.append("\n");
                llmExplainStr.append("初始被污染的参数：");
                for (Integer taintedParam : taintedParams) {
                    llmExplainStr.append("参数" + (taintedParam + 1));
                }
                llmExplainStr.append("\n");
                llmExplainStr.append("初始污点集合（参数被污染到局部变量)：");
                for (TaintNode taintNode : descriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    llmExplainStr.append(taintNode.value.toString() + "; ");
                }
                llmExplainStr.append("\n");

                Set<Value> taintVals = descriptor.getVal_m_taintNode().keySet();
                if(!taintVals.isEmpty()) {
                    // 这里用message2来记录，因为message1是用来记录存在污点改变行为的，我们可能用1非空来判断是否需要保存这一条
                    llmExplainStr.append("初始污点集合(从Val_m_taintNode读)：");
                    for (Value value : taintVals) {
                        llmExplainStr.append(value.toString() + "; ");
                    }
                    llmExplainStr.append("\n");
                }
            }
        }

        if (sootMethod.toString().equals(DEBUG_METHOD)) {
            log.debug("污点参： " + descriptor.getInputTaintParamId());
        }

        if (descriptor.cfg == null) {
            descriptor.cfg = new CFG(sootMethod, true).buildCFG();
        }

//        int taintSetSize = descriptor.allTaintNodes.size();
//        int forwardCallStackSize = callStack.size() - track_ind;
//        int interProcessEdgeSize = cg.edgesOutOf(sootMethod).size();
//        String s = "";
//        s += "sootMethod: " + sootMethod + "\n";
//        s += "taintSetSize: " + taintSetSize + "\n";
//        s += "forwardCallStackSize: " + forwardCallStackSize + "\n";
//        s += "interProcessEdgeSize: " + interProcessEdgeSize + "\n";
//        LogUtil.printBox(s);

        boolean first = true;
        List<TransformableNode> topologicalOrder = TranUtil.getTopologicalOrderedTNodesFromCFG(descriptor.cfg);
        for (TransformableNode transformableNode : topologicalOrder) {
            if(first) {
                transformableNode.boundaryCheckState = StateUtil.mergeBoundaryCheckState(transformableNode.boundaryCheckState, descriptor.incomingBoundaryCheckState);
                first = false;
            }
//            System.out.println(transformableNode.node);
            //应用规则
            transformableNode.forward();
            transformableNode.forward(dataFlowResult);
            transformableNode.constraintsGen(dataFlowResult);

            recordConditionForNullAndThrow(transformableNode, descriptor, topologicalOrder);

            if(TransformableNode.boundary_check_enabled) {
                // 记录boundary_check状态
                if(ConditionRule.tfMapConditionNode.containsKey(transformableNode)) {
                    ConditionNode conditionNode = ConditionRule.tfMapConditionNode.get(transformableNode);
                    TransformableNode.BoundaryCheckState matchBoundaryCheck = StateUtil.matchBoundaryCheck(conditionNode);
                    if(!matchBoundaryCheck.equals(TransformableNode.BoundaryCheckState.NO_CHECK)) {
//                        System.out.println("发现boundary check!");
                        transformableNode.boundaryCheckState = StateUtil.mergeBoundaryCheckState(transformableNode.boundaryCheckState, matchBoundaryCheck);
                    }
                }
            }

            if (sootMethod.toString().equals(DEBUG_METHOD)) {
                log.warn(transformableNode.toString());
                StringBuilder stringBuilder = new StringBuilder();
                for (TaintNode taintNode : descriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    stringBuilder.append(taintNode.value).append(" ");
                }
                log.debug(stringBuilder.toString());
            }

            SootMethod initializeM = null;
            if(transformableNode.equals(topologicalOrder.get(0))) {
                if(CGOptimizer.toBeOptimizedInitialMethods.containsKey(sootMethod.getDeclaringClass())) {
                    initializeM = CGOptimizer.toBeOptimizedInitialMethods.get(sootMethod.getDeclaringClass());
                }
            }
            if (transformableNode.containsInvoke() || (initializeM != null && !finishedClinit.contains(initializeM)) || cg.edgesOutOf(transformableNode.node.unit).size() > 0){

                HashSet<SootMethod> invokes = transformableNode.invokeMethods();
                if(initializeM != null) {
                    invokes.add(initializeM);
                    System.out.println("cg optimize: " + initializeM);
                    finishedClinit.add(initializeM);
                }
//                System.out.println("poly-invoke-size: " + invokes.size());

                // AI-摘要
                long startTime = System.nanoTime();
                int complexityPredict = -1;
                if (IntelligentResolver.ai_predict_record) {

                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                    IntelligentLogger intelligentLogger = intelligentResolver.intelligentLogger;

                    complexityPredict = intelligentResolver.complexityCalculate.calculateComplexity(descriptor, transformableNode);
                    intelligentResolver.complexityProcessor.record(complexityPredict);


                    if (complexityPredict > 100000) {
                        intelligentLogger.log("[Intelligent] call site语句 ：" + transformableNode);
                        intelligentLogger.log("[Intelligent] 预测复杂度 ：" + complexityPredict);
                        intelligentLogger.log("[callstack]: " + callStack);
                    }

                }

                String type_decl = null; // 用于多态调用确认call-target
                for(String value : descriptor.aliasMapType.keySet()) {
                    if(transformableNode.node.toString().contains(value + ".")) {
//                        System.out.println(value + " alias type: " + descriptor.aliasMapType.get(value));
                        type_decl = descriptor.aliasMapType.get(value);
                    }
                }
                for (SootMethod invokeMethod : invokes) {
//                    System.out.println("invoke: " + invokeMethod);
                    if(DEBUG) {
                        System.out.println("[DEBUG] invoke1: " + invokeMethod);
                    }
                    if(invokes.size() > 1) {
                        if(type_decl != null) {
                            if(!invokeMethod.getDeclaringClass().toString().equals(type_decl)) continue;
                        }
                    }
                    // 把java包过滤，不要轻易加
//                    if(invokeMethod.getSignature().startsWith("<java") || invokeMethod.isJavaLibraryMethod()) continue;

                    if(invokeMethod.isPhantom() || !invokeMethod.hasActiveBody()) {
                        if(!(track_ind == callStack.size() - 2)) {
                            // 如果不是倒数第二层调用栈（无需进一步深入）
                            continue;
                        }
                    }
                    if (nextMethod.equals(invokeMethod) || !only_dataflow_callstack_interprocedural) {

                        if(DEBUG) {
                            System.out.println("[DEBUG] invoke2: " + invokeMethod);
                        }

                        // CHA方式下可能需要剪枝
                        if (!CG.isSpark()) {
                            if (CG.mayBeFakeCallByCHA(nextMethod)) {
                                detectContext.setFakeCallStack(true);
                                continue;
                            }
                        }

                        // 记录必需满足的条件约束
                        recordBasicConditions(transformableNode, descriptor, dataFlowResult, false);
                        MethodDescriptor invokeDescriptor = MethodDescriptor.getOrCreateDescriptor(invokeMethod);
                        List<Integer> taintedParams = new LinkedList<>();
                        boolean paramHasTaintedField = false;

                        /**
                         * 判断是否需要重复分析
                         */
                        if(!nextMethod.equals(invokeMethod)) {
                            AtomicBoolean reuseSum = new AtomicBoolean(false);
                            boolean reuseLLMSum = false;
                            // 对于LLM分析过的，一定复用
                            if (MethodDescriptor.methodMapDescriptor.containsKey(invokeMethod)) {
                                MethodDescriptor invokeDescriptor1 = MethodDescriptor.getOrCreateDescriptor(invokeMethod);
                                if (invokeDescriptor1.methodSummary != null) {
                                    if(invokeDescriptor1.methodSummary.tags.contains("LLM")) {
                                        reuseLLMSum = true;
                                    }
                                }
                            }
                            if(summaryReuse && MethodDescriptor.methodMapDescriptor.containsKey(invokeMethod)
                                    && !invokeMethod.getSignature().contains("(boolean)")) {
                                reuseSum.set(true);
                                // 如果有传入的condition，那么不复用
                                descriptor.value2CT.forEach((k, v) -> {
                                    if (v.isEnabled()) {
                                        reuseSum.set(false);
                                    }
                                });
                            }
                            if (reuseSum.get() || reuseLLMSum) {
                                boolean reuse = SummaryReuse.reuseSummary(transformableNode, invokeMethod, descriptor, sootMethod);
                                if(reuse || reuseLLMSum) continue;
                            }
                        }

                        // 首先将上次分析的状态表清空
                        invokeDescriptor.flushStates();

                        /**
                         * 构建call frame
                         */
                        if (invokeDescriptor.cfg == null) {
                            invokeDescriptor.cfg = new CFG(invokeMethod, true).buildCFG();
                        }

                        invokeDescriptor.incomingBoundaryCheckState = StateUtil.mergeBoundaryCheckState(invokeDescriptor.incomingBoundaryCheckState, transformableNode.boundaryCheckState);

                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                            if (argValueBox == null) continue;

                            // 更新alias-type到callee（解决cha误报）
                            if(descriptor.aliasMapType.containsKey(argValueBox.getValue().toString())) {
                                if (invokeDescriptor.paramIdMapLocalValue == null) {
                                    invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                }
                                // 记录callee里local对这个node的map
                                if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value localValue = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    invokeDescriptor.aliasMapType.put(localValue.toString(), descriptor.aliasMapType.get(argValueBox.getValue().toString()));
                                }
                            }

                            // 在构建callframe时传递aliasGraph
                            AccessPathNode parameterAccessPathNode = descriptor.aliasGraph.getNode(argValueBox.getValue());
                            if (parameterAccessPathNode != null) {

                                List<String> taintedAccessPaths = descriptor.aliasGraph.getTaintedAccessPaths(parameterAccessPathNode);
                                //todo. more precise assessments can be made
                                if (taintedAccessPaths != null && taintedAccessPaths.size() > 0) {
                                    /**
                                     * String cmd = source();
                                     * Person person = new Person();
                                     * person.setCmd(cmd);
                                     * CommonUtil.executeCMD(person);
                                     */
                                    paramHasTaintedField = true;
                                }

                                try {
//                                    // System.out.println(parameterAccessPathNode.field);
//                                    // System.out.println(parameterAccessPathNode.values);
                                    AccessPathNode clone = parameterAccessPathNode.cloneSubTree();
                                    if (invokeDescriptor.paramIdMapLocalValue == null) {
                                        invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                    }
                                    // 记录callee里local对这个node的map
                                    if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                        Value localValue = invokeDescriptor.paramIdMapLocalValue.get(i);
                                        clone.values.add(localValue);
                                    }
                                    invokeDescriptor.aliasGraph.addRoot(clone);
                                } catch (CloneNotSupportedException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            // 判断参数是否被污染
                            TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                            if (taintNode != null) {
                                taintedParams.add(i);
//                            List<Taint> allTaintsAboutThisValue = descriptor.getAllTaintsAboutThisValue(argValueBox.getValue());
//                            if(!allTaintsAboutThisValue.isEmpty()) {

                                if (invokeDescriptor.paramIdMapLocalValue == null) {
                                    invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                }

                                // 为第i个参数打taint
                                if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value taintLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    TaintNode taintNode_Callee = invokeDescriptor.getTaintNodeByValue(taintLocal);
                                    if (taintNode_Callee == null) {
                                        taintNode_Callee = new TaintNode(taintLocal, invokeMethod, taintNode.getTaintFlags());
                                        taintNode_Callee.paramInd = i;
                                        invokeDescriptor.addTaintNode(taintNode_Callee);
                                    }

                                    // taintNode是调用者中的局部变量
                                    taintNode.addSuccessor(taintNode_Callee, transformableNode);
                                } else if (track_ind == callStack.size() - 2) {
                                    // 如果被调用者中没找到这个参数的local，而被调用者就是最后一层的sink方法，那么为了
                                    //      sink点的完整性，也要产生假的TaintNode来表示这个参数
                                    //      假的TaintNode的Value填的是调用者中的Value
                                    // 这种情况一般发生在Sink方法没有被soot load进来的时候
                                    TaintNode taintNode_Callee_fake = new TaintNode(argValueBox.getValue(), invokeMethod, taintNode.getTaintFlags());
                                    taintNode_Callee_fake.paramInd = i;
                                    invokeDescriptor.addTaintNode(taintNode_Callee_fake);

                                    taintNode.addSuccessor(taintNode_Callee_fake, transformableNode);
                                }
                            }

                            try {
                                CT ct = new ConditionCollector().collectCT(descriptor, transformableNode, invokeMethod, i, argValueBox);
                                invokeDescriptor.value2CT.put(i, ct);

                                if(ct.isEnabled()) {
                                    if (!detectContext.method_argInd_to_CT.containsKey(invokeMethod.getSignature())) {
                                        detectContext.method_argInd_to_CT.put(invokeMethod.getSignature(), new HashMap<>());
                                    }
                                    if (!detectContext.method_argInd_to_CT.get(invokeMethod.getSignature()).containsKey(i)) {
                                        detectContext.method_argInd_to_CT.get(invokeMethod.getSignature()).put(i, ct);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("【异常】方法：" + invokeMethod);
                                System.out.println("【异常】第几个参数？" + i);
                            }

                        }

                        // 如果是dummy类的方法，我们需要用usebox来确定入参
                        if(SinkDetector.DUMMY_CLASS_NAME.contains(invokeMethod.getDeclaringClass().getName())) {
                            int i = 0;
                            for(ValueBox useBox : transformableNode.node.unit.getUseBoxes()) {
                                TaintNode taintNode = descriptor.getTaintNodeByValue(useBox.getValue());
                                if (taintNode != null) {
                                    taintedParams.add(i);
                                    // 我们不需要做别的，因为dummy方法均是sink方法
                                }
                                i += 1;
                            }
                        }

                        if(!nextMethod.equals(invokeMethod)) {

                            if(invokeMethod.getName().equals("parseAndStore")) {
                                System.out.println("now invoke: " + invokeMethod.getName());
                                System.out.println("detectcontext from entry: " + detectContext.getEntry().getName());
                                System.out.println("hazardmode: " + hazard_mode);
                            }

                            // 在构建完call_frame之后，判断是否冒险hazard，如果冒险，把call_frame中的incoming_states保存后直接略过分析
                            // hazard_mode = 4下，不考虑这些
                            if (hazardOpen && (!(hazard_mode == 4) && hazardMethods.contains(invokeMethod))) {
                                hazard_mode = 1;
                                boolean spread = new TaintNodeRule().process_invoke_risky(transformableNode, descriptor);
                                indeedHazardMethods.add(invokeMethod);
                                // 保存snapshot
                                Snapshot snapshot = new Snapshot(detectContext, track_ind, descriptor, this, invokeMethod);
                                snapshot.taintedParams = invokeDescriptor.getTaintedParams();
                                snapshot.hazardResult = spread;
                                snapshot.invokeNode = transformableNode;
                                hazardM2Snapshot.put(invokeMethod, snapshot);
                                OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                                if (!overAllHazards.method2Snapshot.containsKey(invokeMethod))
                                    overAllHazards.method2Snapshot.put(invokeMethod, new LinkedList<>());
                                overAllHazards.method2Snapshot.get(invokeMethod).add(snapshot);
                                overAllHazards.allHazardedContext.add(detectContext);

                                // 把hazard过的从列表删掉。然后如果全部hazard完了，并且确实是hazard模式，就退出这个context。
                                hazardMethods.remove(invokeMethod);
                                if (hazardMethods.isEmpty() && hazard_mode == 1) {
                                    // 说明确实是hazard模式
                                    hazard_mode = 2; // 这是等待迭代的模式
                                    return retValue;
                                }
                                continue;
                            }

                            OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                            // 如果这个方法被冒险过，但是当前上下文不需要冒险，说明它有足够的incoming states可以摘要
                            if (!(hazard_mode == 4) && overAllHazards.method2Snapshot.containsKey(invokeMethod)) {
                                List<Snapshot> snapshots = overAllHazards.method2Snapshot.get(invokeMethod);
                                Snapshot nowsnapshot = new Snapshot(detectContext, track_ind, descriptor, this, invokeMethod);
                                nowsnapshot.taintedParams = invokeDescriptor.getTaintedParams();
                                nowsnapshot.hazardResult = false;
                                nowsnapshot.invokeNode = transformableNode;
                                snapshots.add(nowsnapshot); // 添加当前的incoming
                                overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                                if(!overAllHazards.method2Snapshot.containsKey(invokeMethod))
                                    overAllHazards.method2Snapshot.put(invokeMethod, new LinkedList<>());
                                overAllHazards.method2Snapshot.get(invokeMethod).add(nowsnapshot);

                                IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentResolver.class).intelligentLogger;
                                intelligentLogger.logFileRPO("[RPO] 足够的incoming states可以摘要 ：" + invokeMethod + " " + snapshots.size());
                                intelligentLogger.logFileRPO("在detectContext: " + detectContext.id);

                                List<Integer> diffentParams = differentiation.diffentiate(snapshots, invokeMethod);
                                HashMap<String, String> diffentiateCT = differentiation.diffentiateCT(snapshots, invokeMethod);
                                if(!diffentParams.isEmpty() || !diffentiateCT.isEmpty()) {
                                    /**
                                     * 复杂上下文使用LLM摘要
                                     */
                                    if (IntelligentResolver.ai_quick_dfg && complexityPredict == -1) {
                                        // 预测调度
                                        IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);

                                        complexityPredict = intelligentResolver.complexityCalculate.calculateComplexity(descriptor, transformableNode);
                                        intelligentResolver.complexityProcessor.record(complexityPredict);
                                        if (complexityPredict > intelligentResolver.maxComplexity_period) {
                                            // 记录一段时间内的最大predict复杂度，这些样本来自主调用栈的过程间调用call site
                                            intelligentResolver.maxComplexity_period = complexityPredict;
                                        }

                                    }

                                    if (IntelligentResolver.ai_quick_dfg && intelligentLogger.conLLMSummary > 500) {
                                        log.warn("语言模型摘要太多，终止LLM摘要（让我省钱）");
                                        IntelligentResolver.ai_quick_dfg = false;
                                    }

                                    if (IntelligentResolver.ai_data_collect_mode) {
    //                            System.out.println("data collect begin: " + invokeMethod.getSignature());

                                        IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);

                                        // 获得call-site的污点状态
                                        List<Integer> csTaintInds = new LinkedList<>();
                                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                            if (argValueBox == null) continue;
                                            TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                            if (taintNode != null) {
                                                csTaintInds.add(i);
                                            }
                                        }
                                        List<SootMethod> call_stack_now = callStack.subList(track_ind + 1, callStack.size());

    //                            System.out.println("data collect: " + invokeMethod.getSignature() + " " + call_stack_now.size() + " " + csTaintInds.size());
//                                        intelligentResolver.dataCollect(invokeMethod, this, call_stack_now, csTaintInds);
                                    }

                                    int P = complexityPredict * (diffentParams.size() + (diffentiateCT.size() * diffentiateCT.size()));
                                    intelligentLogger.logPredict("P: " + P + " " + invokeMethod + " " + diffentParams.size() + " " + diffentiateCT.size() + " " + complexityPredict + " " + detectContext.id);
                                    if (IntelligentResolver.ai_quick_dfg && P > LLMSummary_ComplexityThreshold) {
                                        // 获得call-site的污点状态
                                        List<Integer> csTaintInds = new LinkedList<>();
                                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                            if (argValueBox == null) continue;
                                            TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                            if (taintNode != null) {
                                                csTaintInds.add(i);
                                            }
                                        }

//                                        if (csTaintInds.isEmpty()) continue;
                                        log.warn("调度LLM产生摘要...");
                                        log.warn("预测复杂度: " + complexityPredict);
                                        log.info("callee方法: " + invokeMethod + " 当前方法: " + sootMethod);
                                        log.info("调用栈: " + callStack);

                                        // 传给LLM
                                        IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                                        List<SootMethod> call_stack_now = callStack.subList(track_ind + 1, callStack.size());
                                        try {
                                            log.warn("callsite state: " + csTaintInds);
                                            ArrayList<String> differentConditions = new ArrayList<>(diffentiateCT.values());
                                            intelligentResolver.quickDFG_v2(invokeMethod, this, call_stack_now, csTaintInds, differentConditions);
                                            detectContext.transferMapConditions.putAll(invokeDescriptor.getMethodSummary().transferMapCondition);
                                            log.info("LLM摘要产生成功总数: " + intelligentLogger.conLLMSummary);
                                            SummaryReuse.reuseSummaryDistributive(transformableNode, invokeMethod, descriptor, sootMethod);
                                            intelligentLogger.conLLMSummary += 1;

                                            // 我们恢复其他几个call-site的snapshot并继续分析
                                            // 只需要为对应context做上标记，使得它在摘要模式下再迭代一遍, 届时标记它们进入摘要inline模式（hazard_mode = 4)
                                            // 用LLM摘要的话，我们在这里判断是否冒险失败，todo: 用纯sast的话，我们在invodeMethod分析的末尾判断
                                            // 最终在分析完一个方法后，找到其它冒险的call-site，判断是否需要重新分析
                                            updateContext(invokeMethod);

                                            continue;
                                        } catch (RuntimeException e) {
                                            e.printStackTrace();
                                            intelligentLogger.conLLMSummaryFailed += 1;
                                            log.warn("LLM摘要失败");
                                        }
                                    }

                                }
                            }

                        }

                        if(IntelligentResolver.ai_data_collect_mode) {
//                            System.out.println("data collect begin: " + invokeMethod.getSignature());

                            IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);

                            // 获得call-site的污点状态
                            List<Integer> csTaintInds = new LinkedList<>();
                            for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                if (argValueBox == null) continue;
                                TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                if (taintNode != null) {
                                    csTaintInds.add(i);
                                }
                            }
                            List<SootMethod> call_stack_now = callStack.subList(track_ind + 1, callStack.size());

//                            System.out.println("data collect: " + invokeMethod.getSignature() + " " + call_stack_now.size() + " " + csTaintInds.size());
//                            intelligentResolver.dataCollect(invokeMethod, this, call_stack_now, csTaintInds);
                        }


                        // 如果有污染链，就继续深入
                        if (nextMethod.equals(invokeMethod)) {

                            // 在继续深入前的这里，我们做一下path的检查，用来匹配分支另一头是否有log.warn/ exception这样的标志，进而识别一些关键的path约束
                            StateUtil.checkCondition(transformableNode, dataFlowResult);

                            if(!taintedParams.isEmpty() || paramHasTaintedField) {
                                track_ind = remember_track_ind + 1;
                                if(!waterFallMode) return trackDataFlow_recurse();
                                else {
                                    // 瀑布模式下，保存返回值，并继续分析
                                    retValue = trackDataFlow_recurse();
                                }
                            }
                            else {
                                if(waterFallMode) {
                                    // 瀑布模式下，没有污染，也深入
                                    track_recurse(invokeMethod, track_ind);
                                }
                            }
                        }

                        if (IntelligentResolver.ai_predict_record) {
                            // 以这些调用栈作为样本
                            IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                            intelligentResolver.maxComplexity_period = complexityPredict;
                        }

                        if (!only_dataflow_callstack_interprocedural) {
                            if (timeoutInterprocedural) {
                                timeoutTrack_recurse(invokeMethod, 0);
                            } else {
                                track_recurse(invokeMethod, 0);
                            }
                        }

                        if (IntelligentResolver.ai_predict_record) {
                            // 以这些调用栈作为样本
                            IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                            long endTime = System.nanoTime();
                            long executionTime = endTime - startTime;
                            intelligentResolver.intelligentLogger.logFile2(intelligentResolver.maxComplexity_period + "," + executionTime);
                        }

                        /**
                         * 恢复call frame
                         */
                        // 没有全展开的过程间分析，就不需要恢复
                        // todo: 这里会出现一些"没有头"的taintnode
                        if (!only_dataflow_callstack_interprocedural) {
                            for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                if (invokeDescriptor.paramIdMapLocalValue != null && invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value taintLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    TaintNode taintNode_Callee = invokeDescriptor.getTaintNodeByValue(taintLocal);
                                    if (taintNode_Callee != null) {
                                        ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                        if (argValueBox == null) continue;
                                        TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                        if (taintNode == null) {
                                            // 增加污点
                                            TaintNode taintNode1 = new TaintNode(argValueBox.getValue(), sootMethod);
                                            descriptor.addTaintNode(taintNode1);
                                            // 增加连边，直接处理右值，如果有属于taint的，就连接
                                            Stmt stmt = (Stmt) transformableNode.node.unit;
                                            for(ValueBox valueBox : stmt.getUseBoxes()){
                                                TaintNode taintNode2 = descriptor.getTaintNodeByValue(valueBox.getValue());
                                                if (taintNode2 != null){
                                                    taintNode2.addSuccessor(taintNode1, transformableNode);
                                                }
                                            }

                                            if(TransformableNode.recordExplain) {
                                                transformableNode.explainMessage += "在这句的方法调用中，参数" + i + "被污染，它被反应在变量" + argValueBox.getValue() + "上；";
                                            }
                                        }
                                    }
                                }
                            }

                            // check return value
                            if (invokeDescriptor.isReturnValueTainted()){
                                /**
                                 * String name = webGoatUser.getUsername()
                                 *
                                 * public String getUsername() {
                                 *     return this.username;
                                 * }
                                 */
                                Stmt stmt = (Stmt) transformableNode.node.unit;
                                if (stmt instanceof AssignStmt){
                                    Value leftValue = ((AssignStmt) stmt).getLeftOp();
                                    TaintNode newTaintNode = new TaintNode(leftValue, sootMethod);
                                    descriptor.addTaintNode(newTaintNode);

                                    // 增加连边，直接处理右值，如果有属于taint的，就连接
                                    for(ValueBox valueBox : stmt.getUseBoxes()){
                                        TaintNode taintNode = descriptor.getTaintNodeByValue(valueBox.getValue());
                                        if (taintNode != null){
                                            taintNode.addSuccessor(newTaintNode, transformableNode);
                                        }
                                    }

                                    if(TransformableNode.recordExplain) {
                                        transformableNode.explainMessage += "在这句的方法调用中，返回值被污染，它被反应在变量" + leftValue + "上；";
                                    }
                                }else{
                                    log.info("not support this unit : " + transformableNode.node.unit.toString());
                                }
                            }

                            // clone AliasGraph
                            if (invokeDescriptor.paramIdMapLocalValue == null) {
                                invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                            }
                            for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                // 对每个参数（包括this），把相关的aliasgraph节点都clone一份，如果当前有这个value的节点，作为subtree合并，否则，作为当前方法的aliasgraph的root
                                AccessPathNode nodeInCallee = null;
                                AccessPathNode nodeClone = null;
                                if (invokeDescriptor.paramIdMapLocalValue != null && invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value calleeLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    nodeInCallee = invokeDescriptor.aliasGraph.getNode(calleeLocal);
                                    if (nodeInCallee != null) {
                                        try {
                                            nodeClone = nodeInCallee.cloneSubTree();
                                        } catch (CloneNotSupportedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                if (nodeClone == null) continue;
                                ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                if (argValueBox == null) continue;
                                // 添加变量到node的map
                                nodeClone.values.add(argValueBox.getValue());
                                AccessPathNode accessPathNode = descriptor.aliasGraph.getNode(argValueBox.getValue());
                                if (accessPathNode != null) {
                                    // 我简单的将这个node替换为从callee中clone出来的node
                                    AccessPathNode parent = accessPathNode.parent;
                                    if (parent == null) {
//                                        descriptor.aliasGraph.printNode(nodeClone);
                                        descriptor.aliasGraph.roots.remove(accessPathNode);
                                        descriptor.aliasGraph.addRoot(nodeClone);
                                    } else {
//                                        descriptor.aliasGraph.printNode(nodeClone);
                                        parent.children.remove(accessPathNode);
                                        parent.children.add(nodeClone);
                                    }
                                } else {
                                    descriptor.aliasGraph.addRoot(nodeClone);
                                }
                            }
                        }

                        if(invokeDescriptor.cfg.allNodes.isEmpty()) {
                            // 说明这个方法没有方法体，采用启发式规则，并且记录
                            IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);

                            boolean spread = new TaintNodeRule().process_invoke_risky(transformableNode, descriptor);
                            intelligentLogger.logEmptyMethod("[Intelligent] 方法没有方法体：" + invokeMethod + " spread: " + spread);
                        }
                    }
                }

                if (IntelligentResolver.ai_predict_record) {
                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                    long endTime = System.nanoTime();
                    long executionTime = endTime - startTime;
                    if (complexityPredict > 10000000)
                        intelligentResolver.intelligentLogger.log("过程间分析时间（毫秒）: " + executionTime);
                    if (complexityPredict != -1) {
                        intelligentResolver.complexityProcessor.recordTime(complexityPredict, executionTime);
                        intelligentResolver.intelligentLogger.logFile(complexityPredict + "," + executionTime);
                    }
                }

                // 在每次分析完一个方法后，我们要对srcs集合做更新，用于后续path寻找
                for(TaintNode taintNode : descriptor.sourceTaintNodes) {
                    if(!dataFlowResult.srcs.contains(taintNode)) {
                        dataFlowResult.srcs.add(taintNode);
//                        log.warn("add srcs: " + taintNode);
                    }
                }

            }
        }

        if(TransformableNode.recordExplain) {
            llmExplainStr.append("数据流传播过程： \n");
            llmExplainStr.append(TaintFactData.collectMethodInfo(descriptor, topologicalOrder));
            String explainStr = llmExplainStr.toString();
            if(explainStr.contains("在这条语句")) { // 说明不空
                IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
                intelligentLogger.logLLMExplain(explainStr);
                intelligentLogger.logLLMExplain("<<<<<<<<<<<<< 方法结束 >>>>>>>>>>>>>\n\n");
            }
        }

        // 最终在分析完一个方法后，找到其它冒险的call-site，判断是否需要重新分析
//        updateContext(sootMethod);

        return retValue;
    }

    private long timeoutTrack_recurse(SootMethod sootMethod, int limit) {
//        // 设置超时时间（例如5秒）
//        final long timeout = 10000;
//
//        // 开始时间
//        long startTime = System.nanoTime();
//
//        // 创建一个线程来执行方法
//        Thread thread = new Thread(() -> {
//            try {
//                // 调用你要执行的方法
//                track_recurse(sootMethod, limit);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
//
//        thread.start(); // 开始线程
//        try {
//            thread.join(timeout); // 等待线程完成或超时
//        } catch (InterruptedException e) {
////            throw new RuntimeException(e);
//        }
//
//        // 如果线程仍在运行，则认为超时
//        if (thread.isAlive()) {
//            thread.interrupt(); // 中断线程
//            System.out.println("Method timed out!");
//            return timeout;
//        } else {
//            // 计算执行时间
//            long endTime = System.nanoTime();
//            long duration = (endTime - startTime) / 1_000_000; // 毫秒
////            System.out.println("Method executed in: " + duration + " ms");
//            return duration;
//        }

        new TimeOutTask() {
            @Override
            protected void task() {
                track_recurse(sootMethod, limit);
            }

            @Override
            protected void timeoutHandler() {
                System.out.println("超时");
            }
        }.run(60);

        return 5;
    }

    public static HashSet branchCallMethods = new HashSet();
    public static int con_summaryReuse = 0;
    private void track_recurse(SootMethod sootMethod, int limit) {

        if (limit > waterFallDepth) return;
        detectedMethods.add(sootMethod);
        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentResolver.class).intelligentLogger;

        branchCallMethods.add(sootMethod);

//        log.error("track recurse: " + Util.getSpace(limit * 2) + sootMethod);
        boolean debug = sootMethod.toString().equals(DEBUG_METHOD);

        // 污点清除
        if (taint_sanitize_open) taintSanitizer.sanitizeStringPayload(sootMethod);

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        StringBuilder llmExplainStr = new StringBuilder();
        if(TransformableNode.recordExplain) {
            // 记录初始状态
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams != null) {
                llmExplainStr.append("方法名：" + sootMethod.getName());
                llmExplainStr.append("方法签名：" + sootMethod.getSignature());
                llmExplainStr.append("\n");
                llmExplainStr.append("初始被污染的参数：");
                for (Integer taintedParam : taintedParams) {
                    llmExplainStr.append("参数" + (taintedParam + 1));
                }
                llmExplainStr.append("\n");
                llmExplainStr.append("初始污点集合（参数被污染到局部变量)：");
                for (TaintNode taintNode : descriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    llmExplainStr.append(taintNode.value.toString() + "; ");
                }
                llmExplainStr.append("\n");
            }
        }

        List<Integer> csTaints = descriptor.getTaintedParams();
        boolean retIsTaint = false;
        String csDetailedFact = descriptor.getDetailedDataflowFacts();

        if (sootMethod.toString().equals(DEBUG_METHOD)) {
            log.debug("污点参： " + descriptor.getInputTaintParamId());
        }

        if (descriptor.cfg == null) {
            descriptor.cfg = new CFG(sootMethod, true).buildCFG();
        }

//        int taintSetSize = descriptor.allTaintNodes.size();
//        int forwardCallStackSize = callStack.size() - track_ind;
//        int interProcessEdgeSize = cg.edgesOutOf(sootMethod).size();
//        String s = "";
//        s += "sootMethod: " + sootMethod + "\n";
//        s += "taintSetSize: " + taintSetSize + "\n";
//        s += "forwardCallStackSize: " + forwardCallStackSize + "\n";
//        s += "interProcessEdgeSize: " + interProcessEdgeSize + "\n";
//        LogUtil.printBox(s);

        List<TransformableNode> topologicalOrder = TranUtil.getTopologicalOrderedTNodesFromCFG(descriptor.cfg);
        boolean first = true;
        for (TransformableNode transformableNode : topologicalOrder) {

            if(first) {
                transformableNode.boundaryCheckState = StateUtil.mergeBoundaryCheckState(transformableNode.boundaryCheckState, descriptor.incomingBoundaryCheckState);
                first = false;
            }

            //应用规则
            if(debug) {
                if(transformableNode.node.toString().startsWith("throw")) {
                    System.out.println(11);
                }
            }
            transformableNode.forward();
            transformableNode.forward(dataFlowResult);
            transformableNode.constraintsGen(dataFlowResult);

            recordConditionForNullAndThrow(transformableNode, descriptor, topologicalOrder);
            if(TransformableNode.boundary_check_enabled) {
                // 记录boundary_check状态
                if(ConditionRule.tfMapConditionNode.containsKey(transformableNode)) {
                    ConditionNode conditionNode = ConditionRule.tfMapConditionNode.get(transformableNode);
                    TransformableNode.BoundaryCheckState matchBoundaryCheck = StateUtil.matchBoundaryCheck(conditionNode);
                    if(!matchBoundaryCheck.equals(TransformableNode.BoundaryCheckState.NO_CHECK)) {
//                        System.out.println("发现boundary check!");
                        transformableNode.boundaryCheckState = StateUtil.mergeBoundaryCheckState(transformableNode.boundaryCheckState, matchBoundaryCheck);
                    }
                }
            }

//            if(sootMethod.getName().equals("add_field1_recurse")) {
//                log.warn(transformableNode.toString());
//                System.out.println("all tainted path: ");
//                List<String> allTaintedAccessPaths = descriptor.aliasGraph.getAllTaintedAccessPaths();
//                for(String accessPath : allTaintedAccessPaths) {
//                    System.out.println(accessPath);
//                }
//                System.out.println(descriptor.allTaintNodes);
//                System.out.println("<<<<<<<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>");
//            }
//            log.warn(transformableNode.toString());
//            log.info(descriptor.allTaintNodes.toString());

            if (sootMethod.toString().equals(DEBUG_METHOD)) {
                log.warn(transformableNode.toString());
                intelligentLogger.logDebugCFG(transformableNode.toString());
                StringBuilder stringBuilder = new StringBuilder();
                for (TaintNode taintNode : descriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    stringBuilder.append(taintNode.value).append(" ");
                }
                log.debug(stringBuilder.toString());
                for(TransformableNode suc : transformableNode.successors) {
                    System.out.println("successor: " + suc);
                }
            }

            SootMethod initializeM = null;
            if(transformableNode.equals(topologicalOrder.get(0))) {
                if(CGOptimizer.toBeOptimizedInitialMethods.containsKey(sootMethod.getDeclaringClass())) {
                    initializeM = CGOptimizer.toBeOptimizedInitialMethods.get(sootMethod.getDeclaringClass());
                }
            }
            if (transformableNode.containsInvoke() || (initializeM != null && !finishedClinit.contains(initializeM))) {
                if(debug) {
                    System.out.println("invoke: " + transformableNode.invokeMethods());
                }
                HashSet<SootMethod> invokes = transformableNode.invokeMethods();
                if(initializeM != null) {
                    invokes.add(initializeM);
                    System.out.println("cg optimize: " + initializeM);
                    finishedClinit.add(initializeM);
                }

                // AI-摘要
                long startTime = System.nanoTime();
                int complexityPredict = -1;

                // AI-point-to
                if (IntelligentResolver.ai_point_to && invokes.size() > 0) {
                    // 由于RPC API设计，暂时直接在这里截断，进行source()->sink()的分析
                    // todo: API变成进行指向分析，找准调用就回到正常分析流程
                    // todo: 按需分析
                    if (sootMethod.getDeclaringClass().getName().equals("com.l4yn3.microserviceseclab.controller.PointToTest.PointToAnalysisTest")) {
                        IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                        boolean hasDataflow = intelligentResolver.intelliPointTo(sootMethod, this, callStack, transformableNode, invokes);
                        return;
                    }

                }

                if (invokes.size() > 5) {
                    intelligentLogger.logFile3(transformableNode.toString());
                    intelligentLogger.logFile3("不确定调用数： " + invokes.size());
                    continue;
                }

                String type_decl = null;
                for(String value : descriptor.aliasMapType.keySet()) {
                    if(transformableNode.node.toString().contains(value + ".")) {
//                        System.out.println(value + " alias type: " + descriptor.aliasMapType.get(value));
                        type_decl = descriptor.aliasMapType.get(value);
                    }
                }
                for (SootMethod invokeMethod : invokes) {

                    if(invokes.size() > 1) {
                        if(type_decl != null) {
                            if(!invokeMethod.getDeclaringClass().toString().equals(type_decl)) continue;
                        }
                    }
                    if (!only_dataflow_callstack_interprocedural) {

                        if(invokeMethod.getName().equals("parseAndStore")) {
                            System.out.println("now invoke 1: " + invokeMethod.getName());
                            System.out.println("detectcontext from entry: " + detectContext.getEntry().getName());
                            System.out.println("hazardmode: " + hazard_mode);
                        }

                        if (invokeMethod.getSignature().startsWith("<java") || invokeMethod.isJavaLibraryMethod())
                            continue;

                        if(invokeMethod.getSignature().startsWith("<org.eclipse.jdt")) continue;

                        if(invokeMethod.isPhantom() || !invokeMethod.hasActiveBody()) continue; // 没有规则情况下，phantom方法无法深入

                        /**
                         * 判断是否需要重复分析,如复用，apply后直接continue
                         */
                        AtomicBoolean reuseSum = new AtomicBoolean(false);
                        boolean reuseLLMSum = false;
                        // 对于LLM分析过的，一定复用
                        if (MethodDescriptor.methodMapDescriptor.containsKey(invokeMethod)) {
                            MethodDescriptor invokeDescriptor1 = MethodDescriptor.getOrCreateDescriptor(invokeMethod);
                            if (invokeDescriptor1.methodSummary != null) {
                                if(invokeDescriptor1.methodSummary.tags.contains("LLM")) {
                                    reuseLLMSum = true;
                                }
                            }
                        }
                        if(summaryReuse && MethodDescriptor.methodMapDescriptor.containsKey(invokeMethod)
                                && !invokeMethod.getSignature().contains("(boolean)")) {
                            reuseSum.set(true);
                            // 如果有传入的condition，那么不复用
                            descriptor.value2CT.forEach((k, v) -> {
                                if (v.isEnabled()) {
                                    reuseSum.set(false);
                                }
                            });
                        }
                        if (reuseSum.get() || reuseLLMSum) {
                            boolean reuse = SummaryReuse.reuseSummary(transformableNode, invokeMethod, descriptor, sootMethod);
                            if(reuse || reuseLLMSum) continue;
                        }

                        // 记录必需满足的条件约束
                        // 旁路只在遇到return null 或者 throw的时候记录
//                        recordBasicConditions(transformableNode, descriptor, dataFlowResult);
                        MethodDescriptor invokeDescriptor = MethodDescriptor.getOrCreateDescriptor(invokeMethod);

                        // 首先将上次分析的状态表清空
                        invokeDescriptor.flushStates();

                        /**
                         * 构建call frame
                         */
                        if (invokeDescriptor.cfg == null) {
                            invokeDescriptor.cfg = new CFG(invokeMethod, true).buildCFG();
                        }

                        invokeDescriptor.incomingBoundaryCheckState = StateUtil.mergeBoundaryCheckState(invokeDescriptor.incomingBoundaryCheckState, transformableNode.boundaryCheckState);

                        List<Integer> taintedParams = new LinkedList<>();
                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                            if (argValueBox == null) continue;

                            // 更新alias-type到callee（解决cha误报）
                            if(descriptor.aliasMapType.containsKey(argValueBox.getValue().toString())) {
                                if (invokeDescriptor.paramIdMapLocalValue == null) {
                                    invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                }
                                // 记录callee里local对这个node的map
                                if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value localValue = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    invokeDescriptor.aliasMapType.put(localValue.toString(), descriptor.aliasMapType.get(argValueBox.getValue().toString()));
                                }
                            }

                            // 在构建callframe时传递aliasGraph
                            AccessPathNode parameterAccessPathNode = descriptor.aliasGraph.getNode(argValueBox.getValue());
                            if (parameterAccessPathNode != null) {
                                try {
//                                    // System.out.println(parameterAccessPathNode.field);
//                                    // System.out.println(parameterAccessPathNode.values);
                                    AccessPathNode clone = parameterAccessPathNode.cloneSubTree();
                                    if (invokeDescriptor.paramIdMapLocalValue == null) {
                                        invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                    }
                                    // 记录callee里local对这个node的map
                                    if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                        Value localValue = invokeDescriptor.paramIdMapLocalValue.get(i);
                                        clone.values.add(localValue);
                                    }
                                    invokeDescriptor.aliasGraph.addRoot(clone);
                                } catch (CloneNotSupportedException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            // 判断参数是否被污染
                            TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                            if (taintNode != null) {
                                taintedParams.add(i);
//                            List<Taint> allTaintsAboutThisValue = descriptor.getAllTaintsAboutThisValue(argValueBox.getValue());
//                            if(!allTaintsAboutThisValue.isEmpty()) {

                                if (invokeDescriptor.paramIdMapLocalValue == null) {
                                    invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                                }

                                // 为第i个参数打taint
                                if (invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                    Value taintLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                    TaintNode taintNode_Callee = invokeDescriptor.getTaintNodeByValue(taintLocal);
                                    if (taintNode_Callee == null) {
                                        taintNode_Callee = new TaintNode(taintLocal, invokeMethod, taintNode.getTaintFlags());
                                        taintNode_Callee.paramInd = i;
                                        invokeDescriptor.addTaintNode(taintNode_Callee);
                                    }

                                    // taintNode是调用者中的局部变量
                                    taintNode.addSuccessor(taintNode_Callee, transformableNode);
                                } else if (track_ind == callStack.size() - 2) {
                                    // 如果被调用者中没找到这个参数的local，而被调用者就是最后一层的sink方法，那么为了
                                    //      sink点的完整性，也要产生假的TaintNode来表示这个参数
                                    //      假的TaintNode的Value填的是调用者中的Value
                                    // 这种情况一般发生在Sink方法没有被soot load进来的时候
                                    TaintNode taintNode_Callee_fake = new TaintNode(argValueBox.getValue(), invokeMethod, taintNode.getTaintFlags());
                                    taintNode_Callee_fake.paramInd = i;
                                    invokeDescriptor.addTaintNode(taintNode_Callee_fake);

                                    taintNode.addSuccessor(taintNode_Callee_fake, transformableNode);
                                }
                            }

                            // 判断约束类型的condition
                            try {
                                CT ct = new ConditionCollector().collectCT(descriptor, transformableNode, invokeMethod, i, argValueBox);
                                invokeDescriptor.value2CT.put(i, ct);

                                if(ct.isEnabled()) {
                                    if (!detectContext.method_argInd_to_CT.containsKey(invokeMethod.getSignature())) {
                                        detectContext.method_argInd_to_CT.put(invokeMethod.getSignature(), new HashMap<>());
                                    }
                                    if (!detectContext.method_argInd_to_CT.get(invokeMethod.getSignature()).containsKey(i)) {
                                        detectContext.method_argInd_to_CT.get(invokeMethod.getSignature()).put(i, ct);
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                System.out.println("【异常】方法：" + invokeMethod);
                                System.out.println("【异常】第几个参数？" + i);
                            }

                        }

                        if(invokeMethod.getName().equals("parseAndStore")) {
                            System.out.println("now invoke: " + invokeMethod.getName());
                            System.out.println("detectcontext from entry: " + detectContext.getEntry().getName());
                            System.out.println("hazardmode: " + hazard_mode);
                        }

                        // 在构建完call_frame之后，判断是否冒险hazard，如果冒险，把call_frame中的incoming_states保存后直接略过分析
                        // hazard_mode = 4下，不考虑这些
                        if (hazardOpen && (!(hazard_mode == 4) && hazardMethods.contains(invokeMethod))) {
                            hazard_mode = 1;
                            boolean spread = new TaintNodeRule().process_invoke_risky(transformableNode, descriptor);
                            indeedHazardMethods.add(invokeMethod);
                            // 保存snapshot
                            Snapshot snapshot = new Snapshot(detectContext, track_ind, descriptor, this, invokeMethod);
                            snapshot.taintedParams = invokeDescriptor.getTaintedParams();
                            snapshot.hazardResult = spread;
                            snapshot.invokeNode = transformableNode;
                            hazardM2Snapshot.put(invokeMethod, snapshot);
                            OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                            if(!overAllHazards.method2Snapshot.containsKey(invokeMethod))
                                overAllHazards.method2Snapshot.put(invokeMethod, new LinkedList<>());
                            overAllHazards.method2Snapshot.get(invokeMethod).add(snapshot);
                            overAllHazards.allHazardedContext.add(detectContext);

                            // 把hazard过的从列表删掉。然后如果全部hazard完了，并且确实是hazard模式，就退出这个context。
                            hazardMethods.remove(invokeMethod);
                            if(hazardMethods.isEmpty() && hazard_mode == 1) {
                                // 说明确实是hazard模式
                                hazard_mode = 2; // 这是等待迭代的模式
                                return;
                            }
                            continue;
                        }

                        OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                        // 如果这个方法被冒险过，但是当前上下文不需要冒险，说明它有足够的incoming states可以摘要
                        if(!(hazard_mode == 4) && overAllHazards.method2Snapshot.containsKey(invokeMethod)) {
                            List<Snapshot> snapshots = overAllHazards.method2Snapshot.get(invokeMethod);
                            Snapshot nowsnapshot = new Snapshot(detectContext, track_ind, descriptor, this, invokeMethod);
                            nowsnapshot.taintedParams = invokeDescriptor.getTaintedParams();
                            nowsnapshot.hazardResult = false;
                            nowsnapshot.invokeNode = transformableNode;
                            snapshots.add(nowsnapshot); // 添加当前的incoming
                            overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
                            if(!overAllHazards.method2Snapshot.containsKey(invokeMethod))
                                overAllHazards.method2Snapshot.put(invokeMethod, new LinkedList<>());
                            overAllHazards.method2Snapshot.get(invokeMethod).add(nowsnapshot);

                            intelligentLogger.logFileRPO("[RPO] 足够的incoming states可以摘要 ：" + invokeMethod + " " + snapshots.size());
                            intelligentLogger.logFileRPO("在detectContext: " + detectContext.id);

                            List<Integer> diffentParams = differentiation.diffentiate(snapshots, invokeMethod);
                            HashMap<String, String> diffentiateCT = differentiation.diffentiateCT(snapshots, invokeMethod);
                            if(!diffentParams.isEmpty() || !diffentiateCT.isEmpty()) {
                                /**
                                 * 复杂上下文使用LLM摘要
                                 */
                                if (IntelligentResolver.ai_quick_dfg && complexityPredict == -1) {
                                    // 预测调度
                                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);

                                    complexityPredict = intelligentResolver.complexityCalculate.calculateComplexity(descriptor, transformableNode);
                                    intelligentResolver.complexityProcessor.record(complexityPredict);
                                    if (complexityPredict > intelligentResolver.maxComplexity_period) {
                                        // 记录一段时间内的最大predict复杂度，这些样本来自主调用栈的过程间调用call site
                                        intelligentResolver.maxComplexity_period = complexityPredict;
                                    }

                                }

                                if (IntelligentResolver.ai_quick_dfg && intelligentLogger.conLLMSummary > 500) {
                                    log.warn("语言模型摘要太多，终止LLM摘要（让我省钱）");
                                    IntelligentResolver.ai_quick_dfg = false;
                                }

                                if (IntelligentResolver.ai_data_collect_mode) {
                                    //                            System.out.println("data collect begin: " + invokeMethod.getSignature());

                                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);

                                    // 获得call-site的污点状态
                                    List<Integer> csTaintInds = new LinkedList<>();
                                    for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                        ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                        if (argValueBox == null) continue;
                                        TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                        if (taintNode != null) {
                                            csTaintInds.add(i);
                                        }
                                    }
                                    List<SootMethod> call_stack_now = callStack.subList(track_ind + 1, callStack.size());

                                    //                            System.out.println("data collect: " + invokeMethod.getSignature() + " " + call_stack_now.size() + " " + csTaintInds.size());
//                                    intelligentResolver.dataCollect(invokeMethod, this, call_stack_now, csTaintInds);
                                }

                                int P = complexityPredict * (diffentParams.size() + (diffentiateCT.size() * diffentiateCT.size()));
                                intelligentLogger.logPredict("P: " + P + " " + invokeMethod + " " + diffentParams.size() + " " + diffentiateCT.size() + " " + complexityPredict + " " + detectContext.id);
                                boolean noLLM = false;
                                List<String> noLLMStringList = Arrays.asList("<init>");
                                for(String noLLMString : noLLMStringList) {
                                    if(invokeMethod.getName().contains(noLLMString)) {
                                        noLLM = true;
                                        break;
                                    }
                                }
                                if (IntelligentResolver.ai_quick_dfg && P > LLMSummary_ComplexityThreshold && !noLLM) {
                                    // 获得call-site的污点状态
                                    List<Integer> csTaintInds = new LinkedList<>();
                                    for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                                        ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                        if (argValueBox == null) continue;
                                        TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                        if (taintNode != null) {
                                            csTaintInds.add(i);
                                        }
                                    }

//                                    if (csTaintInds.isEmpty()) continue;
                                    log.warn("调度LLM产生摘要...");
                                    log.warn("预测复杂度: " + complexityPredict);
                                    log.info("callee方法: " + invokeMethod + " 当前方法: " + sootMethod);
                                    log.info("调用栈: " + callStack);
                                    intelligentLogger.logFileRPODiff("此方法要用LLM，因为复杂且多输入。 可能污染或不污染的参数" + diffentParams);


                                    // 传给LLM
                                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                                    List<SootMethod> call_stack_now = callStack.subList(track_ind + 1, callStack.size());
                                    try {
                                        log.warn("callsite state: " + csTaintInds);
//                                    List<Integer> rsTaintInds = new LinkedList<>();
//                                    System.out.println("虚假的LLM，先不调用");
                                        ArrayList<String> differentConditions = new ArrayList<>(diffentiateCT.values());
                                        intelligentResolver.quickDFG_v2(invokeMethod, this, call_stack_now, csTaintInds, differentConditions);
                                        detectContext.transferMapConditions.putAll(invokeDescriptor.getMethodSummary().transferMapCondition);
                                        log.info("LLM摘要产生成功总数: " + intelligentLogger.conLLMSummary);
                                        SummaryReuse.reuseSummaryDistributive(transformableNode, invokeMethod, descriptor, sootMethod);
                                        intelligentLogger.conLLMSummary += 1;

                                        // 我们恢复其他几个call-site的snapshot并继续分析
                                        // 只需要为对应context做上标记，使得它在摘要模式下再迭代一遍, 届时标记它们进入摘要inline模式（hazard_mode = 4)
                                        // 用LLM摘要的话，我们在这里判断是否冒险失败，todo: 用纯sast的话，我们在invodeMethod分析的末尾判断
                                        // 最终在分析完一个方法后，找到其它冒险的call-site，判断是否需要重新分析
                                        updateContext(invokeMethod);

                                        continue;
                                    } catch (RuntimeException e) {
                                        e.printStackTrace();
                                        intelligentLogger.conLLMSummaryFailed += 1;
                                        log.warn("LLM摘要失败");
                                    }
                                }
                            }
                        }


                        /**
                         * 分析callee
                         */
                        if (!only_dataflow_callstack_interprocedural) {
                            if (timeoutInterprocedural) {
                                timeoutTrack_recurse(invokeMethod, limit + 1);
                            } else {
                                track_recurse(invokeMethod, limit + 1);
                            }
                        }

                        /**
                         * 恢复call frame
                         */
                        // todo: 这里会出现一些"没有头"的taintnode
                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                            if (invokeDescriptor.paramIdMapLocalValue != null && invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                Value taintLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                TaintNode taintNode_Callee = invokeDescriptor.getTaintNodeByValue(taintLocal);
                                if (taintNode_Callee != null) {
                                    ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                                    if (argValueBox == null) continue;
                                    TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                                    if (taintNode == null) {
                                        // 增加污点
                                        TaintNode taintNode1 = new TaintNode(argValueBox.getValue(), sootMethod);
                                        descriptor.addTaintNode(taintNode1);

                                        // 增加连边，直接处理右值，如果有属于taint的，就连接
                                        Stmt stmt = (Stmt) transformableNode.node.unit;
                                        for(ValueBox valueBox : stmt.getUseBoxes()){
                                            TaintNode taintNode2 = descriptor.getTaintNodeByValue(valueBox.getValue());
                                            if (taintNode2 != null){
                                                taintNode2.addSuccessor(taintNode1, transformableNode);
                                            }
                                        }

                                        if(TransformableNode.recordExplain) {
                                            transformableNode.explainMessage += "在这句的方法调用中，参数" + i + "被污染，它被反应在变量" + argValueBox.getValue() + "上；";
                                        }
                                    }
                                }
                            }
                        }

                        // check return value
                        if (invokeDescriptor.isReturnValueTainted()){
                            /**
                             * String name = webGoatUser.getUsername()
                             *
                             * public String getUsername() {
                             *     return this.username;
                             * }
                             */
                            Stmt stmt = (Stmt) transformableNode.node.unit;
                            if (stmt instanceof AssignStmt){
                                Value leftValue = ((AssignStmt) stmt).getLeftOp();
                                TaintNode newTaintNode = new TaintNode(leftValue, sootMethod);
                                descriptor.addTaintNode(newTaintNode);
                                // 增加连边，直接处理右值，如果有属于taint的，就连接
                                for(ValueBox valueBox : stmt.getUseBoxes()){
                                    TaintNode taintNode2 = descriptor.getTaintNodeByValue(valueBox.getValue());
                                    if (taintNode2 != null){
                                        taintNode2.addSuccessor(newTaintNode, transformableNode);
                                    }
                                }

                                if(TransformableNode.recordExplain) {
                                    transformableNode.explainMessage += "在这句的方法调用中，返回值被污染，它被反应在变量" + leftValue + "上；";
                                }
                            }else{
                                log.info("not support this unit : " + transformableNode.node.unit.toString());
                            }
                        }

                        // clone AliasGraph
                        if (invokeDescriptor.cfg == null) {
                            invokeDescriptor.cfg = new CFG(invokeMethod, true).buildCFG();
                        }
                        if (invokeDescriptor.paramIdMapLocalValue == null) {
                            invokeDescriptor.paramIdMapLocalValue = Parameter.getParametersLocalValues(invokeDescriptor.cfg);
                        }
//                        log.info("从callee恢复AliasGraph...");
                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                            // 对每个参数（包括this），把相关的aliasgraph节点都clone一份，如果当前有这个value的节点，作为subtree合并，否则，作为当前方法的aliasgraph的root
                            AccessPathNode nodeInCallee = null;
                            AccessPathNode nodeClone = null;
                            if (invokeDescriptor.paramIdMapLocalValue != null && invokeDescriptor.paramIdMapLocalValue.containsKey(i)) {
                                Value calleeLocal = invokeDescriptor.paramIdMapLocalValue.get(i);
                                nodeInCallee = invokeDescriptor.aliasGraph.getNode(calleeLocal);
                                if (nodeInCallee != null) {
                                    try {
                                        nodeClone = nodeInCallee.cloneSubTree();
                                    } catch (CloneNotSupportedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            if (nodeClone == null) continue;

                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                            if (argValueBox == null) continue;
                            // 添加变量到node的map
                            nodeClone.values.add(argValueBox.getValue());
                            AccessPathNode accessPathNode = descriptor.aliasGraph.getNode(argValueBox.getValue());
                            if (accessPathNode != null) {
                                // System.out.println("找到caller中的node");
                                // 我简单的将这个node替换为从callee中clone出来的node
                                AccessPathNode parent = accessPathNode.parent;
                                if (parent == null) {
                                    descriptor.aliasGraph.roots.remove(accessPathNode);
                                    descriptor.aliasGraph.addRoot(nodeClone);
                                } else {
                                    parent.children.remove(accessPathNode);
                                    parent.children.add(nodeClone);
                                }
                            } else {
                                // System.out.println("未找到caller中的node");
                                descriptor.aliasGraph.addRoot(nodeClone);
                            }
                        }
//                        System.out.println("<<<<<<<<<<<<<<<<<< graph 3 in " + sootMethod.getName() + " >>>>>>>>>>>>>>>>>>>>>");
//                        descriptor.aliasGraph.printGraph();

                        if(invokeDescriptor.cfg.allNodes.isEmpty()) {
                            // 说明这个方法没有方法体，采用启发式规则，并且记录
                            boolean spread = new TaintNodeRule().process_invoke_risky(transformableNode, descriptor);
                            intelligentLogger.logEmptyMethod("[Intelligent] 方法没有方法体：" + invokeMethod + " spread: " + spread);
                        }
                        if(limit == waterFallDepth) {
                            // 说明下一个方法没有被展开分析
                            boolean spread = new TaintNodeRule().process_invoke_risky(transformableNode, descriptor);
                        }

                    }
                }

                if (IntelligentResolver.ai_predict_record) {
                    IntelligentResolver intelligentResolver = SpringContextUtils.getBean(IntelligentResolver.class);
                    long endTime = System.nanoTime();
                    long executionTime = endTime - startTime;
                    if (complexityPredict > 10000000)
                        intelligentResolver.intelligentLogger.log("过程间分析时间（毫秒）: " + executionTime);
                    if (complexityPredict != -1) {
                        intelligentResolver.complexityProcessor.recordTime(complexityPredict, executionTime);
                        intelligentResolver.intelligentLogger.logFile(complexityPredict + "," + executionTime);
                    }
                }
            }

            // 如果是返回语句，判断返回值是否被污染
            if (transformableNode.node.unit.toString().startsWith("return ")) {
                ReturnStmt returnStmt = (ReturnStmt) transformableNode.node.unit;
                Value returnValue = returnStmt.getOp();
                if (returnValue != null) {
                    TaintNode taintNode = descriptor.getTaintNodeByValue(returnValue);
                    if (taintNode != null) {
                        retIsTaint = true;
                    }
                }
            }
        }

//        System.out.println("<<<<<<<<<<<<<<<<<< graph 2 in " + sootMethod.getName() + "  >>>>>>>>>>>>>>>>>>>>>");
//        descriptor.aliasGraph.printGraph();

        List<Integer> rsTaints = descriptor.getTaintedParams();
        if (retIsTaint) {
            rsTaints.add(-2); // -2 表示返回值被污染
            descriptor.setReturnValueTainted(true);
        }
        descriptor.methodSummary.addConditionalTaint(csTaints, rsTaints);
        descriptor.methodSummary.tags.add("SAST");
        String rsDetailedFact = descriptor.getDetailedDataflowFacts();
        descriptor.methodSummary.detailedTransferMap.put(csDetailedFact, rsDetailedFact);

        if(TransformableNode.recordExplain) {
            llmExplainStr.append("数据流传播过程： \n");
            llmExplainStr.append(TaintFactData.collectMethodInfo(descriptor, topologicalOrder));
            String explainStr = llmExplainStr.toString();
            if(explainStr.contains("在这条语句")) { // 说明不空
                intelligentLogger.logLLMExplain(explainStr);
                intelligentLogger.logLLMExplain("<<<<<<<<<<<<< 方法结束 >>>>>>>>>>>>>\n\n");
            }
        }

        // 最终在分析完一个方法后，找到其它冒险的call-site，判断是否需要重新分析
//        updateContext(sootMethod);
    }

    // sootMethod分析结束后，更新其它hazard的context确保其重新迭代
    private void updateContext(SootMethod sootMethod) {
        System.out.println("updateContext: " + sootMethod.getName());
        // 最终在分析完一个方法后，找到其它冒险的call-site，判断是否需要重新分析
        OverAllHazards overAllHazards = SpringContextUtils.getBean(OverAllHazards.class);
        // 如果这个方法被冒险过，但是当前上下文不需要冒险，说明它有足够的incoming states可以摘要
        if (!(hazard_mode == 4) && overAllHazards.method2Snapshot.containsKey(sootMethod)) {
            List<Snapshot> snapshots = overAllHazards.method2Snapshot.get(sootMethod);
            for (Snapshot snapshot : snapshots) {
                DetectContext anotherContext = snapshot.detectContext;
                if (anotherContext == detectContext) continue;
                System.out.println("context " + anotherContext.getId());
                MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
                boolean needUpdate = false;
                if(descriptor.isReturnValueTainted() != snapshot.hazardResult) {
                    needUpdate = true;
                }

                if(needUpdate) {
                    System.out.println("冒险 " + snapshot.hazardResult + " 修正 " + descriptor.isReturnValueTainted());
                    overAllHazards.needUpdateContext.add(anotherContext);
                }
            }
        }
    }

    private void findAndInjectSrces() {
        srcs = new LinkedList<>();

        // 默认将所有参数作为src
        SootMethod srcMethod = callStack.get(0);
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(srcMethod);
        // 这里刷新一次状态，在summary-reuse等级高的情况下，这里可能也有上次分析状态没清理
        descriptor.flushStates();
        CFG cfg = new CFG(srcMethod, true).buildCFG();
        HashMap<Integer, Value> parametersLocalValues = Parameter.getParametersLocalValues(cfg);
        // 刷新
        descriptor.allTaintNodes = new LinkedList<>();

        SpringEntrySet springEntrySet = SpringContextUtils.getBean(SpringEntrySet.class);
        ServletEntrySet servletEntrySet = SpringContextUtils.getBean(ServletEntrySet.class);
        Knows knows = SpringContextUtils.getBean(Knows.class);
        if(knows.methodInputMap.containsKey(srcMethod)) {
            // 如果knows里指定了参数
            log.debug("为knows中的方法添加污点源");
            List<Integer> inputParamInds = knows.methodInputMap.get(srcMethod);
            for (Integer paramInd : parametersLocalValues.keySet()) {
                Value value = parametersLocalValues.get(paramInd);
                TaintNode taintNode = new TaintNode(value, srcMethod);
                taintNode.paramInd = paramInd;
                if (inputParamInds.contains(paramInd)) {
                    taintNode.srcMessage = new SrcMessage(paramInd, false, null, null);
                    srcs.add(taintNode);
                    descriptor.addTaintNode(taintNode);
                }
            }
        } else if (springEntrySet.getEntries().contains(srcMethod) || servletEntrySet.getEntries().contains(srcMethod)) {
            // spring中带postmapping等注解的controller：所有参数（除了this）都是输入
            log.debug("为Spring中的Controller方法添加污点源");

            HashMap<Integer, PayloadStructure> entryMapPayloadStructures = new HashMap<>();
            if (springEntrySet.entryMapPayloadStructures.containsKey(srcMethod)) {
                // 如果有payload结构，就用payload结构作为输入
                for (PayloadStructure payloadStructure : springEntrySet.entryMapPayloadStructures.get(srcMethod)) {
                    int paramInd = payloadStructure.entryParamInd;
                    entryMapPayloadStructures.put(paramInd, payloadStructure);
                }
            }

            for (Integer paramInd : parametersLocalValues.keySet()) {
                if (paramInd == -1) continue;
                Value value = parametersLocalValues.get(paramInd);
                TaintNode taintNode = new TaintNode(value, srcMethod);
                taintNode.paramInd = paramInd;

                // 如果具有结构信息
                if (entryMapPayloadStructures.containsKey(paramInd)) {
                    PayloadStructure payloadStructure = entryMapPayloadStructures.get(paramInd);
                    log.error(String.format("加入结构化的污点源：{}", payloadStructure));
                    taintNode.srcMessage = new SrcMessage(paramInd, true, payloadStructure, payloadStructure.baseAccessPath);
                } else {
                    taintNode.srcMessage = new SrcMessage(paramInd, false, null, null);
                }

                // todo. simulated data
                String[] taintFlags = {Constants.TAINT_FLAGS_ALL};
                taintNode.setTaintFlags(taintFlags);

                srcs.add(taintNode);
                descriptor.addTaintNode(taintNode);
            }
        } else {
            log.error("knows没有指定参数，并且非springboot和servlet的方法，所有参数都是污点源（除了this）");
            // 其他情况：所有参数，不包括this，都是污点
            for (Integer paramInd : parametersLocalValues.keySet()) {
                if(paramInd == -1) continue;
                Value value = parametersLocalValues.get(paramInd);
                TaintNode taintNode = new TaintNode(value, srcMethod);
                taintNode.paramInd = paramInd;
                srcs.add(taintNode);
                descriptor.addTaintNode(taintNode);
            }
        }

    }

    public static String getExpression(List<Element> elements, List<String> taintFlags) {
        List<Element> elementList = orderElements(elements);
        StringBuilder result = new StringBuilder();
        for (Element ele : elementList) {
            String eleName = ele.getName();
            if (result.length() > 1) {
                if (eleName.equals("Or")) {
                    result.append(" || ");
                } else {
                    result.append(" && ");
                }
            }
            result.append(parseElement(ele, taintFlags));
        }
        return result.toString();
    }


    public static List<Element> orderElements(List<Element> elements) {
        List<Element> elementListLeft = new LinkedList<>();
        List<Element> elementListRight = new LinkedList<>();
        Iterator<Element> iterator = elements.iterator();
        while (iterator.hasNext()) {
            Element child = iterator.next();
            if (child.getName().equals("Or")) {
                elementListRight.add(child);
            } else {
                elementListLeft.add(child);
            }
        }
        elementListLeft.addAll(elementListRight);
        return elementListLeft;
    }

    public static String parseElement(Element element, List<String> taintFlags) {
        String nodeName = element.getName();
        if (nodeName.equals("And") || nodeName.equals("Or")) {
            StringBuilder result = new StringBuilder("(");
            List<Element> elements = orderElements(element.elements());
            for (Element child : elements) {
                if (result.length() > 1) {
                    if (child.getName().equals("Or")) {
                        result.append(" || ");
                    } else {
                        result.append(" && ");
                    }
                }
                result.append(parseElement(child, taintFlags));
            }
            result.append(")");
            return result.toString();
        } else if (nodeName.equals("Not")) {
            StringBuilder result = new StringBuilder("!(");
            List<Element> elements = orderElements(element.elements());
            for (Element child : elements) {
                result.append(parseElement(child, taintFlags));
            }
            result.append(")");
            return result.toString();
        } else if (nodeName.equals("TaintFlagSet")) {
            String taintFlag = element.attributeValue("taintFlag");
            if (taintFlags.contains("+" + taintFlag)) {
                return "true";
            } else {
                return "false";
            }
        } else {
            return "false";
        }
    }


    public boolean matchConditional(Element element, String[] taintFlags) {
        // no condition, then matched
        if (element == null) {
            return true;
        }
        if (taintFlags == null || taintFlags.length == 0) {
            return false;
        }
        // +ALL, then always matched
        if (taintFlags[0].equals(Constants.TAINT_FLAGS_ALL)) {
            return true;
        }
        List<String> taintFlagList = Arrays.asList(taintFlags);

        String expression = getExpression(element.elements(), taintFlagList);
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(expression);
        Boolean condition = (Boolean) exp.getValue();
        return condition;
    }

    private Boolean catchSinks(SootMethod sinkMethod) {
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sinkMethod);
        Knows knows = SpringContextUtils.getBean(Knows.class);
        Map<String, List<SootMethod>> sootMethodlistMap = knows.getSinkTypes();

        // 如果是条件sink，先检查条件
        ConditionalSinks conditionalSinks = SpringContextUtils.getBean(ConditionalSinks.class);
        if(conditionalSinks.conditionalSinks.contains(sinkMethod.getSignature())) {
            if(!conditionalSinks.conditionValid(sinkMethod.getSignature(), descriptor)) {
                log.warn("条件sink的条件不满足，假的sink");
                return false;
            }
        }

        // todo: 不知道为何会丢失knows里的sink
        if(sinkMethod.getSignature().equals("<org.apache.catalina.Session: void setNote(java.lang.String,java.lang.Object)>")) {
            knows.getSinksParams().put(sinkMethod, Collections.singletonList(1));
        }

        if (knows.getSinksParams().containsKey(sinkMethod)) {
            List<Integer> sinkParams = knows.getSinksParams().get(sinkMethod);

            sinks = new LinkedList<>();
            for (TaintNode taintNode : descriptor.allTaintNodes) {

                //todo. conditional
                Element conditional = knows.getConditional().get(sinkMethod.getSignature());
                boolean judge = matchConditional(conditional, taintNode.getTaintFlags());
                if (!judge) {
                    continue;
                }
                if (sinkParams.contains(taintNode.paramInd)) {
                    sinks.add(taintNode);
                }
            }
            return !sinks.isEmpty();
        } else {
            sinks = descriptor.allTaintNodes;
            return true;
        }
    }

    private boolean containsTaintNode(TaintNode taintNode) {
        for (TaintNode node : sinks) {
            if (node.toString().equals(taintNode.toString())) {
                return true;
            }
        }
        return false;
    }

    public void recordBasicConditions(TransformableNode tfNode,
                                      MethodDescriptor descriptor,
                                      DataFlowResult dataFlowResult,
                                      boolean badFlag) {
        HashSet<Integer> path_record = tfNode.path_record;
        for (Integer hashCode : path_record) {
            if (path_record.contains(-hashCode))
                continue;

            boolean satisfyFlag = hashCode > 0;

            TransformableNode addConditionTfNode = ConditionRule.conditionTfNodesMap.get(hashCode > 0 ? hashCode : -hashCode);
            IfStmt addIfStmt = Parameter.getIfStmt(addConditionTfNode);
            ConditionNode recordedConditionNode = null;
            for (ConditionNode conditionNode : dataFlowResult.conditionNodes) {
                if (conditionNode == null) continue;
                if (conditionNode.ifStmt == null)
                    continue;
                if (conditionNode.ifStmt.equals(addIfStmt)) {
                    recordedConditionNode = conditionNode;
                    break;
                }
            }

            if (recordedConditionNode != null) {
                ConditionNode tmpConditionNode = new ConditionNode(addConditionTfNode, descriptor, satisfyFlag, true);
                if(badFlag) tmpConditionNode.badConditionFlag = true;
//                dataFlowResult.conditionNodes.add(tmpConditionNode);
//                ConditionRule.putConditionNode(dataFlowResult, tmpConditionNode);
                int index = dataFlowResult.conditionNodes.indexOf(recordedConditionNode);
                dataFlowResult.conditionNodes.remove(index);
                dataFlowResult.conditionNodes.add(index, tmpConditionNode);
            } else {
                dataFlowResult.conditionNodes.remove(recordedConditionNode);
                ConditionNode tmpConditionNode = new ConditionNode(addConditionTfNode, descriptor, satisfyFlag, true);
                if(badFlag) tmpConditionNode.badConditionFlag = true;
//                dataFlowResult.conditionNodes.remove(tmpConditionNode);
                ConditionRule.putConditionNode(dataFlowResult, tmpConditionNode);
                if(badFlag) {
                    // hack: bad condition永远会被直接记录
                    dataFlowResult.conditionNodes.add(tmpConditionNode);
                    // 检查badcondition是否满足
                    if(tmpConditionNode.relatedCT != null) {
                        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
                        intelligentLogger.logCondition("check bad condition: " + tmpConditionNode + " is satisfied?");
                        intelligentLogger.logCondition("related incoming: " + tmpConditionNode.relatedCT);

                        boolean satisfy = checkSatisfy(tmpConditionNode, tmpConditionNode.relatedCT);
                        if(!satisfy) {
                            intelligentLogger.logCondition("bad condition not satisfied");
                            detectContext.conditionNotSatisfy = true;
                            detectContext.conditionNotSatisfyReason = "【bad condition not satisfied】 " + tmpConditionNode.relatedCT;
                        } else {
                            intelligentLogger.logCondition("bad condition satisfied");
                            detectContext.conditionSatisfy = true;
                            detectContext.conditionSatisfyReason = "【bad condition satisfied】 " + tmpConditionNode.relatedCT;
                        }
                    }
                }
            }
        }
    }

    public void recordConditionForNullAndThrow(TransformableNode transformableNode, MethodDescriptor descriptor, List<TransformableNode> topologicalOrder) {
        // 在遇到return null 或者 throw的时候记录路径条件
        if(transformableNode.node.unit.toString().startsWith("throw ")){
            recordBasicConditions(transformableNode, descriptor, dataFlowResult, true);

            if(transformableNode.boundaryCheckState != TransformableNode.BoundaryCheckState.NO_CHECK) {
                dataFlowResult.hasBoundaryCheck = true;
            }
        }
        if(transformableNode.node.unit.toString().startsWith("return ")) {
            List<ValueBox> useBoxes = transformableNode.node.unit.getUseBoxes();
            boolean retNull = false;
            TransformableNode defNullNode = null;
            for(ValueBox valueBox : useBoxes) {
                List<Node> defNodes = DataFlow.getDefNodes(valueBox, transformableNode.node);
                for(Node node : defNodes) {
                    if(node.unit.toString().endsWith(" = null")) {
                        retNull = true;
                        for(TransformableNode tNode : topologicalOrder) {
                            if(tNode.node == node) {
                                defNullNode = tNode;
                            }
                        }
                    }
                }
            }
            if(retNull && defNullNode != null) {
                // 两句：r1 = null return r1 这里我们用前一句作为提取路径约束的tnode，因为可能存在多条路径汇聚到return句的情况
                recordBasicConditions(defNullNode, descriptor, dataFlowResult, true);
            }
        }
        if(transformableNode.node.unit.toString().equals("return null")) {
            recordBasicConditions(transformableNode, descriptor, dataFlowResult, true);
        }
    }

    public boolean checkSatisfy(ConditionNode conditionNode, CT ct) {
        boolean match = true;
        for(String key : conditionNode.constants.keySet()) {
            if(key.equals("int") || key.equals("boolean")) {
                if(ct.CT2_enable) {
                    if(!String.valueOf(ct.CT2).equals(conditionNode.constants.get(key))) {
                        match = false;
                    }
                }
            }
        }
        if(conditionNode.comparison.equals(Comparison.NO_EQUAL_TO)) {
            // 此时反转
            return !match;
        } else return match;
    }

    public static class DataFlowResult {
        public List<TaintNode> getSrcs() {
            return srcs;
        }

        public void setSrcs(List<TaintNode> srcs) {
            this.srcs = srcs;
        }

        public List<TaintNode> getSinks() {
            return sinks;
        }

        public void setSinks(List<TaintNode> sinks) {
            this.sinks = sinks;
        }

        public boolean isHasDataFlow() {
            return hasDataFlow;
        }

        // 记录条件约束信息
//        @JsonIgnore
        public LinkedList<ConditionNode> conditionNodes = new LinkedList<>();
//        @JsonIgnore
        public LinkedList<Integer> conditionHashCodes = new LinkedList<>();
//        @JsonIgnore
        public HashSet<UsedSiteRecord> usedSiteRecords = new HashSet<>();

        public int getComplexity() {
            return complexity;
        }

        private int complexity = 0;

        public void setHasDataFlow(boolean hasDataFlow) {
            this.hasDataFlow = hasDataFlow;
        }

        private List<TaintNode> srcs; // 上层入口方法的所有参数
        private List<TaintNode> sinks; // 危险方法的所有参数
        private boolean hasDataFlow;

        public boolean hasBoundaryCheck = false;
        public ConditionNode boundaryCheckConditionNode = null;
        public boolean hasFilePathCheck = false;

        public List<List<TaintNode>> getDataPaths() {
            return dataPaths;
        }

        public void addDataPath(List<TaintNode> dataPath) {
            if (dataPaths == null) {
                dataPaths = new LinkedList<>();
            }
            dataPaths.add(dataPath);
        }

        private List<List<TaintNode>> dataPaths;

        public void calculateComplexity() {
            // 根据conditionNodes的复杂度
            for (ConditionNode conditionNode : conditionNodes) {
                if (conditionNode != null) {
                    complexity += conditionNode.getComplexity();
                }
            }
        }
    }

    private HashSet<SootMethod> finishedClinit = new HashSet<>();

}
