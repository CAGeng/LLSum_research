package detect.dataflow;

import TranModel.Rule;
import TranModel.Rules.*;
import TranModel.TranUtil;
import TranModel.TransformableNode;
import TranModel.algorithm.Dijkstra;
import TranModel.datastruct.ConditionNode;
import TranModel.datastruct.PayloadStructure;
import TranModel.datastruct.SrcMessage;
import TranModel.datastruct.TaintNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import app.utils.Constants;
import basic.DefaultDetector.MethodDescriptor;
import basic.DefaultDetector.MethodSummary;
import basic.cfg.CFG;
import basic.cg.CG;
import basic.dataflow.fieldsensitive.AccessPathNode;
import basic.util.LogUtil;
import basic.util.StaticAnalyzeUtils.InvokeStmtUtil;
import basic.util.StaticAnalyzeUtils.Parameter;
import basic.util.TimeOutTask;
import com.fasterxml.jackson.annotation.JsonIgnore;
import detect.DetectContext;
import detect.knowledge.Knows;
import detect.knowledge.constraint.ClassCastConstraint;
import detect.knowledge.constraint.ContainsConstraint;
import detect.knowledge.constraint.EqualsConstraint;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.knowledge.entry.webapp.ServletEntrySet;
import detect.knowledge.sink.ConditionalSinks;
import detect.unit.instrument.UsedSiteRecord;
import intelligent.IntelligentLogger;
import intelligent.IntelligentResolver;
import intelligent.datacollect.TaintFactData;
import intelligent.predict.ComplexityCalculate;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;

import java.util.*;

/*
    数据流追踪： 分析出数据流事实
 */
@Slf4j
public class DataFlowSummarize {

    public SootMethod entry;
    public List<TaintNode> srcs;

    public CG cg;
    public int waterFallDepth = 5; // 瀑布模式下的展开深度
    public boolean summaryReuse = true;

    public List<Integer> input_state_taints = null; // 指明传入污点，否则默认全污染（除了this）

    public DataFlowSummarize(SootMethod entry, CG cg) {
        this.entry = entry;
        this.cg = cg;
    }

    public DataFlowSummarize(SootMethod entry, CG cg, List<Integer> input_taints) {
        this.entry = entry;
        this.cg = cg;
        this.input_state_taints = input_taints;
    }

    public void summarizeDataflow() {
        TranUtil.init(cg);
        ComplexityCalculate.cg = cg;
        TransformableNode.clearRules();
        TransformableNode.clearInfoRules();
        TransformableNode.clearConstraintGen();
        TransformableNode.addRule(new JoinRule()); // 这个必须第一个加入
        TransformableNode.addRule(new TaintNodeRule());
        TransformableNode.addRule(new TestcaseSourceRule());
        TransformableNode.addRule(new RecordLLMExplainRule());
        TransformableNode.addInfoRule(new ConditionRule());
        TransformableNode.addConstraintGen(new ClassCastConstraint());
        TransformableNode.addConstraintGen(new ContainsConstraint());
        TransformableNode.addConstraintGen(new EqualsConstraint());

        RuleCollection ruleCollection = SpringContextUtils.getBean(RuleCollection.class);
        Map<String, Rule> ruleMap = ruleCollection.getPropagationRuleMap();
        Iterator<Map.Entry<String, Rule>> entries = ruleMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, Rule> entry = entries.next();
            Rule rule = entry.getValue();
            TransformableNode.addRule(rule);
        }

        // 注入Src
        findAndInjectSrces();

        // ai-test: 每次刷一遍testcaseSource规则中可能通过source = new Source()产生的source
        TestcaseSourceRule.testSources = new LinkedList<>();

        // System.out.println("DEBUG: " + DEBUG_METHOD);
        track_recurse(entry, 0);

    }

    public String DEBUG_METHOD = "<org.owasp.webgoat.lessons.pathtraversal.ProfileUploadRetrieval: org.springframework.http.ResponseEntity getProfilePicture(javax.servlet.http.HttpServletRequest)>";

    private void track_recurse(SootMethod sootMethod, int limit) {

        if (limit > waterFallDepth) return;

        log.error("trackrecurse: " + sootMethod);

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
        for (TransformableNode transformableNode : topologicalOrder) {
            //应用规则
            transformableNode.forward();
//            transformableNode.forward(dataFlowResult);
//            transformableNode.constraintsGen(dataFlowResult);

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
                StringBuilder stringBuilder = new StringBuilder();
                for (TaintNode taintNode : descriptor.allTaintNodes) {
                    if(taintNode.value == null) continue;
                    stringBuilder.append(taintNode.value).append(" ");
                }
                log.debug(stringBuilder.toString());
            }
            if (transformableNode.containsInvoke()) {
                HashSet<SootMethod> invokes = transformableNode.invokeMethods();

                if (invokes.size() > 5) {
                    IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
                    intelligentLogger.logFile3(transformableNode.toString());
                    intelligentLogger.logFile3("不确定调用数： " + invokes.size());
                    continue;
                }

                for (SootMethod invokeMethod : invokes) {
                    if (invokeMethod.getSignature().startsWith("<java") || invokeMethod.isJavaLibraryMethod())
                        continue;

                    if(invokeMethod.getSignature().startsWith("<org.eclipse.jdt")) continue;

                    /**
                     * 判断是否需要重复分析,如复用，apply后直接continue
                     */
                    if (summaryReuse && MethodDescriptor.methodMapDescriptor.containsKey(invokeMethod)) {
                        List<Integer> csTaintInds = new LinkedList<>();
                        for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                            ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                            if (argValueBox == null) continue;
                            TaintNode taintNode = descriptor.getTaintNodeByValue(argValueBox.getValue());
                            if (taintNode != null) {
                                csTaintInds.add(i);
                            }
                        }
                        MethodSummary methodSummary = MethodDescriptor.methodMapDescriptor.get(invokeMethod).methodSummary;
                        methodSummary.reuse_con += 1;
                        List<Integer> rsTaints = methodSummary.getConditionalTaint(csTaintInds);
                        if (rsTaints != null) {
                            // 说明摘要能够复用
//                                log.warn("复用摘要： " + methodSummary.methodSig);
                            for (int ind : rsTaints) {
                                if (ind > Parameter.getArgSize(transformableNode.node) - 1) continue;
                                try {
                                    Value value = null;
                                    if (ind == -2) {
                                        // 返回值被污染
                                        Value retValue2 = InvokeStmtUtil.getReturnValue(transformableNode.node.unit);
                                        value = retValue2;
                                    } else {
                                        ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, ind);
                                        if (argValueBox == null) continue;
                                        value = argValueBox.getValue();
                                    }
                                    if (value == null) continue;
                                    TaintNode taintNode = descriptor.getTaintNodeByValue(value);
                                    if (taintNode == null) {
                                        // 增加污点
                                        TaintNode taintNode1 = new TaintNode(value, sootMethod);
                                        descriptor.addTaintNode(taintNode1);
                                        // 增加连边
                                        for (int i : csTaintInds) {
                                            ValueBox argValueBox_cs = Parameter.getArgValueBox(transformableNode.node, i);
                                            if (argValueBox_cs == null) continue;
                                            TaintNode taintNode_cs = descriptor.getTaintNodeByValue(argValueBox_cs.getValue());
                                            if (taintNode_cs != null) {
                                                taintNode_cs.addSuccessor(taintNode1, transformableNode);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            continue;
                        }
                    }

                    if(invokeMethod.isPhantom() || !invokeMethod.hasActiveBody()) continue; // 没有规则情况下，phantom方法无法深入
                    MethodDescriptor invokeDescriptor = MethodDescriptor.getOrCreateDescriptor(invokeMethod);

                    // 首先将上次分析的状态表清空
                    invokeDescriptor.flushStates();

                    /**
                     * 构建call frame
                     */
                    List<Integer> taintedParams = new LinkedList<>();
                    for (int i = -1; i < Parameter.getArgSize(transformableNode.node); i++) {
                        ValueBox argValueBox = Parameter.getArgValueBox(transformableNode.node, i);
                        if (argValueBox == null) continue;

                        // 在构建callframe时传递aliasGraph
                        AccessPathNode parameterAccessPathNode = descriptor.aliasGraph.getNode(argValueBox.getValue());
                        if (parameterAccessPathNode != null) {
                            try {
//                                    // System.out.println(parameterAccessPathNode.field);
//                                    // System.out.println(parameterAccessPathNode.values);
                                AccessPathNode clone = parameterAccessPathNode.cloneSubTree();
                                if (invokeDescriptor.cfg == null) {
                                    invokeDescriptor.cfg = new CFG(invokeMethod, true).buildCFG();
                                }
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
                            if (invokeDescriptor.cfg == null) {
                                invokeDescriptor.cfg = new CFG(invokeMethod, true).buildCFG();
                            }

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
                            }
                        }
                    }

                    /**
                     * 分析callee
                     */
                    track_recurse(invokeMethod, limit + 1);

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
                IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
                intelligentLogger.logLLMExplain(explainStr);
                intelligentLogger.logLLMExplain("<<<<<<<<<<<<< 方法结束 >>>>>>>>>>>>>\n\n");
            }
        }
    }

    private void findAndInjectSrces() {
        srcs = new LinkedList<>();

        // 默认将所有参数作为src
        SootMethod srcMethod = entry;
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(srcMethod);
        // 这里刷新一次状态，在summary-reuse等级高的情况下，这里可能也有上次分析状态没清理
        descriptor.flushStates();
        CFG cfg = new CFG(srcMethod, true).buildCFG();
        HashMap<Integer, Value> parametersLocalValues = Parameter.getParametersLocalValues(cfg);
        // 刷新
        descriptor.allTaintNodes = new LinkedList<>();

        SpringEntrySet springEntrySet = SpringContextUtils.getBean(SpringEntrySet.class);
        ServletEntrySet servletEntrySet = SpringContextUtils.getBean(ServletEntrySet.class);
        if (springEntrySet.getEntries().contains(srcMethod) || servletEntrySet.getEntries().contains(srcMethod)) {
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
            log.error("非springboot和servlet的方法，所有参数都是污点源（除了this）");
            // 其他情况：所有参数，不包括this，都是污点
            for (Integer paramInd : parametersLocalValues.keySet()) {
                if(paramInd == -1) continue;
                if(input_state_taints == null || input_state_taints.contains(paramInd)) {
                    Value value = parametersLocalValues.get(paramInd);
                    TaintNode taintNode = new TaintNode(value, srcMethod);
                    taintNode.paramInd = paramInd;
                    srcs.add(taintNode);
                    descriptor.addTaintNode(taintNode);
                }
            }
        }

    }


}
