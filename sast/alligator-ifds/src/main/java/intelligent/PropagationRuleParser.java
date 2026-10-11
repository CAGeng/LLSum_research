package intelligent;

import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import soot.RefType;
import soot.SootClass;
import soot.SootField;
import soot.Type;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.*;

public class PropagationRuleParser {

    public static class RuleInfo {
        public String namespace;
        public String className;
        public String methodName;
        public String condition;
        public List<String> inArguments;
        public List<String> outArguments;

        // toString方法
        @Override
        public String toString() {
            return "RuleInfo{" +
                    "namespace='" + namespace + '\'' +
                    ", className='" + className + '\'' +
                    ", methodName='" + methodName + '\'' +
                    ", condition='" + condition + '\'' +
                    ", inArguments=" + inArguments +
                    ", outArguments=" + outArguments +
                    '}';
        }

        // Getters and setters
        // [可根据需要补充]
    }

    public static List<RuleInfo> parsePropagationRules(String xmlContent) throws Exception {
        List<RuleInfo> result = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(xmlContent.getBytes()));

        NodeList ruleNodes = doc.getElementsByTagName("PropagationRule");

        for (int i = 0; i < ruleNodes.getLength(); i++) {
            Element ruleElement = (Element) ruleNodes.item(i);

            // 检查HasPropagation是否为true
            Element hasPropagationElement = (Element) ruleElement.getElementsByTagName("HasPropagation").item(0);
            if (!Boolean.parseBoolean(hasPropagationElement.getTextContent())) {
                continue;
            }

            RuleInfo ruleInfo = new RuleInfo();

            // 解析FunctionIdentifier部分
            Element funcIdentifier = (Element) ruleElement.getElementsByTagName("FunctionIdentifier").item(0);
            ruleInfo.namespace = funcIdentifier.getElementsByTagName("NamespaceName")
                    .item(0).getTextContent().trim();
            ruleInfo.className = funcIdentifier.getElementsByTagName("ClassName")
                    .item(0).getTextContent().trim();
            ruleInfo.methodName = funcIdentifier.getElementsByTagName("FunctionName")
                    .item(0).getTextContent().trim();

            // 解析Condition
            Element conditionElement = (Element) ruleElement.getElementsByTagName("Condition").item(0);
            if (conditionElement != null) {
                ruleInfo.condition = conditionElement.getTextContent().trim();
            }

            // 解析InArguments和OutArguments
            Element inArgsElement = (Element) ruleElement.getElementsByTagName("InArguments").item(0);
            if (inArgsElement != null) {
                ruleInfo.inArguments = Arrays.asList(inArgsElement.getTextContent().split(",\\s*"));
            }

            Element outArgsElement = (Element) ruleElement.getElementsByTagName("OutArguments").item(0);
            if (outArgsElement != null) {
                ruleInfo.outArguments = Arrays.asList(outArgsElement.getTextContent().split(",\\s*"));
            }

            result.add(ruleInfo);
        }

        return result;
    }

    public static void verifyAndRefineRules(List<RuleInfo> rules, MethodDescriptor invokeDescriptor) {
        // 结合def-use分析对ruleinfo进行解析并修正，直接保存到descriptor
        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);
        for(RuleInfo ruleInfo : rules) {
            List<Integer> rsTaintsAll = new LinkedList<>();
            for(String arg : ruleInfo.outArguments) {
                List<Integer> rsTaints = refineArg(arg, invokeDescriptor);
                rsTaintsAll.addAll(rsTaints);
            } // 实际上LLM生成的，一般outArguments 和 inArguments里只有一个
            List<Integer> csTaintsAll = new LinkedList<>();
            for(String arg : ruleInfo.inArguments) {
                List<Integer> csTaints = refineArg(arg, invokeDescriptor);
                csTaintsAll.addAll(csTaints);
            }
            if(rsTaintsAll.contains(-2)) {
                invokeDescriptor.setReturnValueTainted(true);
            }

            boolean parseSuccess = false;
            try {
                ConditionParser.ParseResult condition = ConditionParser.parseCondition(ruleInfo.condition);
                invokeDescriptor.methodSummary.addConditionalTaint(csTaintsAll, rsTaintsAll);
                invokeDescriptor.methodSummary.tags.add("LLM");
                invokeDescriptor.methodSummary.transferMapCondition.put(hashTransfer(csTaintsAll, rsTaintsAll), condition);
                intelligentLogger.logFileQueryLLM("record LLM summary: " + hashTransfer(csTaintsAll, rsTaintsAll) + " \nin condition: " + condition.toString());
                parseSuccess = true;
            } catch (Exception e) {
                System.err.println("Error parsing: " + ruleInfo.condition + " - " + e.getMessage());
            }

            if(!parseSuccess) {
                // 这里对于bool形参数，可能给出a->result的数据流，增加容错，把pi == true这个条件记录
                for(String arg : ruleInfo.inArguments) {
                    List<Integer> csTaints = refineArg(arg, invokeDescriptor);
                    for(int ind : csTaints) {
                        if(invokeDescriptor.sootMethod.getParameterType(ind).toString().equals("boolean")) {
                            String conditionStr = "p" + ind + " == true";
                            ConditionParser.ParseResult condition = ConditionParser.parseCondition(conditionStr);
                            invokeDescriptor.methodSummary.addConditionalTaint(csTaintsAll, rsTaintsAll);
                            invokeDescriptor.methodSummary.tags.add("LLM");
                            invokeDescriptor.methodSummary.transferMapCondition.put(hashTransfer(csTaintsAll, rsTaintsAll), condition);
                            intelligentLogger.logFileQueryLLM("record LLM summary: " + hashTransfer(csTaintsAll, rsTaintsAll) + " \nin condition: " + condition.toString());
                            parseSuccess = true;
                        }
                    }
                }
            }

        }
    }

    private static String hashTransfer(List<Integer> csTaintsAll, List<Integer> rsTaintsAll) {
        // 生成hash
        StringBuilder sb = new StringBuilder();
        for(Integer i : csTaintsAll) {
            sb.append("c"+i);
        }
        sb.append("->");
        for(Integer i : rsTaintsAll) {
            sb.append("r"+i);
        }
        return sb.toString();
    }

    private static List<Integer> refineArg(String arg, MethodDescriptor descriptor) {
        // 对参数进行修正，可能的情况：-1.session
        List<Type> parameterTypes = descriptor.sootMethod.getParameterTypes();
        SootClass thisClass = descriptor.sootMethod.getDeclaringClass();

        List<Integer> rsTaints = new LinkedList<>();
        boolean indFound = false, fieldFound = false;

        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentLogger.class);

        String[] split = arg.split("\\.");
        if (split.length == 1) {
            String argIndex = split[0];
            int index = 0;
            try {
                index = Integer.parseInt(argIndex);
                rsTaints.add(index);
                indFound = true;
            } catch (Exception e) {
            }
        }
        if (split.length == 2) {
            String argIndex = split[0];
            String argField = split[1];

            int index = 0;
            try {
                index = Integer.parseInt(argIndex);
                rsTaints.add(index);
                indFound = true;
            } catch (Exception e) {
            }
            if(indFound) {
                SootField foundField = null;
                if (index >= 0 && index < parameterTypes.size()) {
                    Type type = parameterTypes.get(index);
                    if (type instanceof RefType) {
                        RefType refType = (RefType) type;
                        SootClass argClass = refType.getSootClass();
                        for (SootField field : argClass.getFields()) {
                            if (field.getName().equals(argField)) {
                                fieldFound = true;
                                intelligentLogger.logFileQueryLLM("Refine arg: " + arg + " to " + field.getName());
                            }
                        }
                        if(!fieldFound) {
                            for (SootField field : argClass.getFields()) {
                                if (field.getName().contains(argField)) {
                                    fieldFound = true;
                                    intelligentLogger.logFileQueryLLM("Refine arg: " + arg + " to " + field.getName());
                                }
                            }
                        }
                    }
                }
                if(fieldFound) {
                    // todo: 这里更新field级别污点
                }
            }
        }

        return rsTaints;
    }

    public static void main(String[] args) throws Exception {
        String xmlContent = "<PropagationRule>\n" +
                "    <RuleID>RULE-ID-PROPAGATION-00000005</RuleID>\n" +
                "    <FunctionIdentifier>\n" +
                "        <NamespaceName>\n" +
                "            <Value>org.apache.hadoop.io.compress</Value>\n" +
                "        </NamespaceName>\n" +
                "        <ClassName>\n" +
                "            <Value>BlockDecompressorStream</Value>\n" +
                "        </ClassName>\n" +
                "        <FunctionName>\n" +
                "            <Value>getCompressedData</Value>\n" +
                "        </FunctionName>\n" +
                "        <ApplyTo implements=\"true\" overrides=\"true\" extends=\"true\"/>\n" +
                "    </FunctionIdentifier>\n" +
                "    <Parameters>\n" +
                "        <!-- No parameters for this method -->\n" +
                "    </Parameters>\n" +
                "    <HasPropagation>true</HasPropagation>\n" +
                "    <Condition>NOC</Condition>\n" +
                "    <InArguments>buffer</InArguments>\n" +
                "    <OutArguments>return</OutArguments>\n" +
                "    <Comment>\n" +
                "    - The data flow shows that the buffer and potentially in.lastBytesSent influence the return value.\n" +
                "    - The flow is no condition\n" +
                "    </Comment>\n" +
                "</PropagationRule>"; // 你的XML内容
        List<RuleInfo> rules = PropagationRuleParser.parsePropagationRules(xmlContent);

        for (RuleInfo rule : rules) {
            System.out.println("Class: " + rule.className);
            System.out.println("Method: " + rule.methodName);
            System.out.println("Condition: " + rule.condition);
            System.out.println("InArgs: " + rule.inArguments);
            System.out.println("OutArgs: " + rule.outArguments);
        }
    }
}
