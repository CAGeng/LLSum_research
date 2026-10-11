package intelligent;

import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.util.StaticAnalyzeUtils.MethodUtil;
import detect.dataflow.DataFlowTrack;
import detect.summary.QuickSummarization;
import intelligent.predict.ComplexityCalculate;
import intelligent.predict.ComplexityProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;
import sourceprocess.DatabaseManager;
import sourceprocess.MethodInfoService;

import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class IntelligentResolver {

    public static boolean ai_quick_dfg = false;
    public static boolean ai_predict_record = false;
    public static boolean ai_point_to = false;

    public static boolean ai_data_collect_mode = false; // 提取ai训练数据的模式

    public boolean slice = false;

    public HashMap<SootMethod, String> LLMSumMethod2result = new HashMap<>();

    @Autowired
    public ComplexityProcessor complexityProcessor;

    @Autowired
    public ComplexityCalculate complexityCalculate;

    @Autowired
    public IntelligentLogger intelligentLogger;

    @Autowired
    private MethodInfoService methodInfoService;

    QuickSummarization quickSummarization = new QuickSummarization();


    public long maxComplexity_period = 0; // 一段时间内的最高复杂度

    public boolean quickDFG(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack) {

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        String aiInput = getSrcCode(sootMethod, dataFlowTrack, callStack);
        log.error(aiInput);

        // 2.2. 访问提供GPT-prompt服务的python端
        GptRpcProxy gptRpcProxy = SpringContextUtils.getBean(GptRpcProxy.class);
        String dfg = gptRpcProxy.quick_dfg(aiInput);
        log.error("------------------ 使用ai能力 ---------------------");
        log.error(dfg);
        if(dfg.contains("false")) {
            return false;
        }

        // 3. 处理结果格式保存到对象
        // todo: 处理结果格式保存到对象
        // 构建sink表示借助GPT找到了sink
        TaintNode taintNode = new TaintNode(null, sootMethod);
        taintNode.assignUnit = "[AI] " + dfg;
        dataFlowTrack.sinks.add(taintNode);
        // 把这个方法的所有污点参数都连接到AI产生的污点序列
        for(TaintNode fromNode : descriptor.allTaintNodes) {
            fromNode.addSuccessor(taintNode, null);
        }
        return true;
    }

    // ai_data_collect_mode提取训练数据（与分析过程无关）
    public void dataCollect(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack, List<Integer> csTaints) {
        String srcCodeSlice = getSrcCodeSlice(sootMethod, dataFlowTrack, callStack, csTaints);
        String srcCode = getSrcCode(sootMethod, dataFlowTrack, callStack);

        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
        methodDescriptor.methodSummary.sliceCode = srcCodeSlice;
        methodDescriptor.methodSummary.code = srcCode;

//        System.out.println("srcCodeSlice: " + srcCodeSlice);
//        System.out.println("srcCode: " + srcCode);
    }

    public void quickDFG_v2(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack,
                                     List<Integer> csTaints, List<String> conditionDiffs) {

//        return csTaints;

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
        String aiInput = "";
        if(slice) {
            aiInput = getSrcCodeSlice(sootMethod, dataFlowTrack, callStack, csTaints);
        } else {
            aiInput = getSrcCodeFromDatabase(sootMethod, dataFlowTrack, callStack);
        }
//        log.error(aiInput);
        if(aiInput == "") {
            log.error("未找到对应代码段");
            throw new RuntimeException("aiInput is empty");
        }

        intelligentLogger.logFileRPODiff("源代码从数据库读出：");
        intelligentLogger.logFileRPODiff(aiInput);

//        // 2.2. 访问提供GPT-prompt服务的python端
        // 查看缓存的LLM结果
        if(LLMSumMethod2result.containsKey(sootMethod)) {
            String result = LLMSumMethod2result.get(sootMethod);
            System.out.println("使用缓存的LLM结果: " + sootMethod.getSignature());
            try {
                List<PropagationRuleParser.RuleInfo> ruleInfos = PropagationRuleParser.parsePropagationRules(result);
                PropagationRuleParser.verifyAndRefineRules(ruleInfos, descriptor);
            } catch (Exception e) {
                System.out.println("解析LLM query结果失败: " + result);
            }
        } else {
            GptRpcProxy gptRpcProxy = SpringContextUtils.getBean(GptRpcProxy.class);
            String result = gptRpcProxy.quick_dfg_v2(aiInput, csTaints, conditionDiffs);
            LLMSumMethod2result.put(sootMethod, result);
            log.error("------------------ 使用ai能力 ---------------------");
            log.error(result);
            try {
                List<PropagationRuleParser.RuleInfo> ruleInfos = PropagationRuleParser.parsePropagationRules(result);
                System.out.println("LLM query结果：");
                for(PropagationRuleParser.RuleInfo ruleInfo : ruleInfos) {
                    System.out.println(ruleInfo);
                    intelligentLogger.logFileQueryLLM("LLM query代码: " + aiInput + "\n" + csTaints + "\n" + conditionDiffs + "\n");
                    intelligentLogger.logFileQueryLLM("LLM query结果：\n" + ruleInfo + "\n");
                }

                PropagationRuleParser.verifyAndRefineRules(ruleInfos, descriptor);
            } catch (Exception e) {
//            System.out.println("解析LLM query结果失败: " + result);
            }
        }
    }

    public boolean intelliPointTo(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack, TransformableNode transformableNode, HashSet<SootMethod> candidateMethods) {
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        List<SootMethod> onDemandMethods = new LinkedList<>();
        StringBuilder sb = new StringBuilder();

        sb.append("---------------入口方法-------------\n");
        String sourceCode = SourceCodeUtil.getMethodSourceCode(callStack.get(0));
        sb.append(sourceCode).append("\n");
        onDemandMethods.add(callStack.get(0));

        sb.append("---------------调用链上方法-------------\n");
        for(int i = 0; i < callStack.size(); i ++) {
            SootMethod method = callStack.get(i);
            if(onDemandMethods.contains(method)) continue; // 不重复
            onDemandMethods.add(method);
            String sc = SourceCodeUtil.getMethodSourceCode(method);
            sb.append(sc).append("\n");
        }

        sb.append("---------------相关方法-------------\n");
        for(SootMethod method : candidateMethods) {
            if(onDemandMethods.contains(method)) continue; // 不重复
            onDemandMethods.add(method);
            String sc = SourceCodeUtil.getMethodSourceCode(method);
            sb.append(sc).append("\n");
        }

//        sb.append("---------------其他调用的方法-------------\n");
//        List<SootMethod> allCallees = MethodUtil.getAllCallees(sootMethod, dataFlowTrack.cg, 2); // 包含自己，一共只探索2层，或者说1层被调用者
//        for(SootMethod method : allCallees) {
//            if(onDemandMethods.contains(method)) continue; // 不重复
//            onDemandMethods.add(method);
//            String sc = SourceCodeUtil.getMethodSourceCode(method);
//            sb.append(sc).append("\n");
//        }

        String aiInput = sb.toString();
        log.error(aiInput);

        // 2.2. 访问提供GPT-prompt服务的python端
        GptRpcProxy gptRpcProxy = SpringContextUtils.getBean(GptRpcProxy.class);
        String dfg = gptRpcProxy.quick_dfg(aiInput);
        log.error("------------------ 使用ai能力 ---------------------");
        log.error(dfg);
        if(dfg.contains("false")) {
            return false;
        }

        // 3. 处理结果格式保存到对象
        // todo: 处理结果格式保存到对象
        // 构建sink表示借助GPT找到了sink
        TaintNode taintNode = new TaintNode(null, sootMethod);
        taintNode.assignUnit = "[AI point-to] " + dfg;
        dataFlowTrack.sinks.add(taintNode);
        // 把这个方法的所有污点参数都连接到AI产生的污点序列
        for(TaintNode fromNode : descriptor.allTaintNodes) {
            fromNode.addSuccessor(taintNode, null);
        }
        return true;
    }

    private String getSrcCode(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack) {

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        // 1.1 我决定在这里做一个on-demand的分析，对方法调用进行一些展开，一起喂给gpt
        // 1.2 寻找对应方法源码/jimple源码
        List<SootMethod> onDemandMethods = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("---------------入口方法-------------\n");
        String sourceCode = SourceCodeUtil.getMethodJimpleCode(sootMethod);
        if(sourceCode == "") return "";
        sb.append("方法：").append(sootMethod.getSignature()).append("\n");
        sb.append(sourceCode).append("\n");
        onDemandMethods.add(sootMethod);

//        sb.append("---------------调用链上其它方法-------------\n");
//        for(int i = dataFlowTrack.track_ind + 1; i < callStack.size(); i ++) {
//            SootMethod method = callStack.get(i);
//            if(onDemandMethods.contains(method)) continue; // 不重复
//            onDemandMethods.add(method);
//            String sc = SourceCodeUtil.getMethodJimpleCode(method);
//            sb.append(sc).append("\n");
//        }
//

        sb.append("---------------其他调用的方法-------------\n");
        List<SootMethod> allCallees = MethodUtil.getAllCallees(sootMethod, dataFlowTrack.cg, 3);
        int limit = 0;
        List<String> easyMethods = Arrays.asList("toString", "equals", "hashCode", "getClass", "<init>", "<clinit>", "debug", "error", "log", "trace", "warn", "info", "printStackTrace");
        for(SootMethod method : allCallees) {
            if(onDemandMethods.contains(method)) continue; // 不重复
//            if(method.isJavaLibraryMethod() || method.getSignature().startsWith("<java") || easyMethods.contains(method.getName())) continue;
//            if(method.getParameterCount() == 0) continue;
//            limit += 1;
//            if(limit > 5) break;
            onDemandMethods.add(method);
            String sc = SourceCodeUtil.getMethodJimpleCode(method);
            sb.append("方法：").append(method.getSignature()).append("\n");
            sb.append(sc).append("\n");
        }
        return sb.toString();
    }

    private String getSrcCodeFromDatabase(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack) {

        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        // 1.1 我决定在这里做一个on-demand的分析，对方法调用进行一些展开，一起喂给gpt
        // 1.2 寻找对应方法源码
        List<SootMethod> onDemandMethods = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("---------------入口方法-------------\n");
        String className = sootMethod.getDeclaringClass().getName();
        String methodName = sootMethod.getName();
        String sourceCode = methodInfoService.searchMethod(className, methodName).toString();
        if(sourceCode == "") return "";
        sb.append("方法：").append(sootMethod.getSignature()).append("\n");
        sb.append(sourceCode).append("\n");
        onDemandMethods.add(sootMethod);

//        sb.append("---------------调用链上其它方法-------------\n");
//        for(int i = dataFlowTrack.track_ind + 1; i < callStack.size(); i ++) {
//            SootMethod method = callStack.get(i);
//            if(onDemandMethods.contains(method)) continue; // 不重复
//            onDemandMethods.add(method);
//            String sc = SourceCodeUtil.getMethodJimpleCode(method);
//            sb.append(sc).append("\n");
//        }
//

        sb.append("---------------其他相关的方法-------------\n");
        List<SootMethod> allCallees = MethodUtil.getAllCallees(sootMethod, dataFlowTrack.cg, 2);
        int limit = 0;
        List<String> easyMethods = Arrays.asList("toString", "equals", "hashCode", "getClass", "<init>", "<clinit>", "debug", "error", "log", "trace", "warn", "info", "printStackTrace");
        for(SootMethod method : allCallees) {
            if(onDemandMethods.contains(method)) continue; // 不重复
            if(method.isJavaLibraryMethod() || method.getSignature().startsWith("<java") || easyMethods.contains(method.getName())) continue;
//            if(method.getParameterCount() == 0) continue;
            limit += 1;
            if(limit > 3) break;
            if(!sb.toString().contains(method.getName() + "(")) continue; // 如果不包含，说明可能该方法上层已经被semantic inline，那么不需要提供源代码
            onDemandMethods.add(method);
            String className1 = method.getDeclaringClass().getName();
            String methodName1 = method.getName();
            String sc = methodInfoService.searchMethod(className1, methodName1).toString();

            String semanticInline = getSemanticInlineIfPossible(method, sc);
            if(!semanticInline.equals("")) {
                // 直接把inline结果放在注释里
                // 按";"分割 StringBuilder 内容
                String[] lines = sb.toString().split(";");
                // 清空 StringBuilder
                sb.setLength(0);
                // 逐行遍历，查找并替换目标行
                for (String line : lines) {
                    boolean isInvokeLine = false;
                    if(line.contains(method.getName() + "(")) {
                        if(!line.contains("public") && !line.contains("private") && !line.contains("protected") && !line.contains("方法")) {
                            isInvokeLine = true;
                        }
                    }
                    if (isInvokeLine && !line.contains("// [dataflow in this line")) {
                        String replacementLine = line + " // [dataflow in this line] " + semanticInline;
                        replacementLine = replaceParamName(replacementLine, method);
                        sb.append(replacementLine);  // 替换目标行
                    } else {
                        sb.append(line);  // 保留其他行
                    }
                    sb.append(";");  // 逐行添加换行符
                }
            } else {
                if(!sc.isEmpty()) {
                    sb.append("方法：").append(method.getSignature()).append("\n");
                    sb.append(sc).append("\n");
                }
            }

        }
        return sb.toString();
    }

    // 如果适合semantic inline，返回inline结果，否则返回""，表示不适合inline
    private String getSemanticInlineIfPossible(SootMethod method, String sourceCode) {
        if(MethodDescriptor.methodMapDescriptor.containsKey(method)) {
            // 这个方法已经有摘要，考虑用摘要semantic inline
            MethodDescriptor descriptor = MethodDescriptor.methodMapDescriptor.get(method);
            HashMap<String, String> detailedTransferMap = descriptor.methodSummary.detailedTransferMap;
            StringBuilder sb = new StringBuilder();
            for(String cs : detailedTransferMap.keySet()) {
                String rs = detailedTransferMap.get(cs);
                // 替换掉换行符
                cs = formatDetailedTaint(cs);
                rs = formatDetailedTaint(rs);
                rs = rs.replace(cs, ""); // 去掉cs的内容, 只考虑新增的污点，防止这个inline太冗长
                if(cs.equals("[]") || rs.equals("[]")) continue;
                if(cs.equals("") || rs.equals("")) continue;
                sb.append("This line involves dataflow from [").append(cs).append("] to [").append(rs).append("]； ");
            }
            return sb.toString();
        }
        if(sourceCode.split("\n").length < 10) {
            // 如果源码行数小于10，考虑inline
            HashMap<Integer, List<Integer>> summarizeResult = quickSummarization.summarize(method);
            if(summarizeResult.size() > 0) {
                StringBuilder sb = new StringBuilder();
                for(Integer cs : summarizeResult.keySet()) {
                    List<Integer> rs = summarizeResult.get(cs);
                    if(cs == null || rs == null) continue;
                    if(rs.isEmpty()) continue;
                    sb.append("This line involves dataflow from [").append(cs).append("] to [").append(rs).append("]； ");
                }
                if(sb.length() > 0) return sb.toString();
            }
            return "该方法内没有数据流";
        }
        return "";
    }

    private String getSrcCodeSlice(SootMethod sootMethod, DataFlowTrack dataFlowTrack, List<SootMethod> callStack, List<Integer> csTaints) {
        MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);

        // 1.1 我决定在这里做一个on-demand的分析，对方法调用进行一些展开，一起喂给gpt
        // 1.2 寻找对应方法源码/jimple源码
        List<SootMethod> onDemandMethods = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("---------------入口方法-------------\n");
        String sourceCode = SourceCodeUtil.getMethodJimpleCode_DFGBasedSlice(sootMethod, csTaints);
        if(sourceCode == "") return "";
        sb.append("方法：").append(sootMethod.getSignature()).append("\n");
        sb.append(sourceCode).append("\n");
        onDemandMethods.add(sootMethod);

//        sb.append("---------------调用链上其它方法-------------\n");
//        for(int i = dataFlowTrack.track_ind + 1; i < callStack.size(); i ++) {
//            SootMethod method = callStack.get(i);
//            if(onDemandMethods.contains(method)) continue; // 不重复
//            onDemandMethods.add(method);
//            String sc = SourceCodeUtil.getMethodJimpleCode(method);
//            sb.append(sc).append("\n");
//        }

        sb.append("---------------其他调用的方法-------------\n");
        List<SootMethod> allCallees = MethodUtil.getAllCallees(sootMethod, dataFlowTrack.cg, 3);
        int limit = 0;
        List<String> easyMethods = Arrays.asList("toString", "equals", "hashCode", "getClass", "<init>", "<clinit>", "debug", "error", "log", "trace", "warn", "info", "printStackTrace");
        for(SootMethod method : allCallees) {
            if(onDemandMethods.contains(method)) continue; // 不重复
            if(method.isJavaLibraryMethod() || method.getSignature().startsWith("<java") || easyMethods.contains(method.getName())) continue;
            if(method.getParameterCount() == 0) continue;
            limit += 1;
            if(limit > 5) break;
            onDemandMethods.add(method);
            String sc = SourceCodeUtil.getMethodJimpleCode(method);
            sb.append("方法：").append(method.getSignature()).append("\n");
            sb.append(sc).append("\n");
        }
        return sb.toString();
    }

    private String formatDetailedTaint(String raw) {
        // [dataflow in this line] This line involves dataflow from param0; param1; param2; to param0; param1 : field-all; param2 : field-all; return; ；
        // This line involves dataflow from param-1 : [root]->lastBytesSent; param-1 : [root]->lastBytesSent; param2 : field-all; toparam-1 : [root]->lastBytesSent; param-1 : [root]->lastBytesSent; param2 : field-all; return; ； This line involves dataflow fromparam0 : field-all; toparam0 : field-all; return; ； This line involves dataflow fromparam-1 : [root]->chunkPos; param-1 : [root]->lastBytesSent; toparam-1 : [root]->chunkPos; param-1 : [root]->lastBytesSent; return; ； This line involves dataflow fromparam-1 : [root]->lastBytesSent; param-1 : [root]->lastBytesSent; toparam-1 : [root]->lastBytesSent; param-1 : [root]->lastBytesSent; return; ； This line involves dataflow fromparam-1 : [root]->chunkPos; toparam-1 : [root]->chunkPos; ；
        String res;
        res = raw.replace("\n", ", ");
        res = res.replace(" : field-all", "");
        res = res.replace(" : [root]->", ".");
        return res;
    }

    private String replaceParamName(String raw, SootMethod method) {
        // 用实参替换形参
        String res = raw;

        // List to hold the arguments
        List<String> argumentList = new ArrayList<>();

        // Define the regular expression pattern
        Pattern pattern = Pattern.compile(method.getName() + "\\(([^)]+)\\)");
        Matcher matcher = pattern.matcher(raw);
        // Find and extract the arguments
        if (matcher.find()) {
            String arguments = matcher.group(1); // Get the captured group
            // Split the arguments by comma and trim whitespace
            String[] argsArray = arguments.split(",");
            for (String arg : argsArray) {
                argumentList.add(arg.trim());
            }
        }

        res = res.replace("param-1", "this");
        for(int ind = 0; ind < argumentList.size(); ind ++) {
            res = res.replace("param" + ind, argumentList.get(ind));
        }

        return res;
    }
}
