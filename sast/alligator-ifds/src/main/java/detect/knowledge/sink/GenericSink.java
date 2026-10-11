package detect.knowledge.sink;

import app.importer.config.RulesConfig;
import app.importer.entity.Form;
import app.importer.entity.FunctionIdentifier;
import app.importer.entity.Sink;
import app.importer.entity.SinkRule;
import app.utils.Constants;
import app.utils.GenericUtil;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Element;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;


@Setter
@Getter
@Component
@Slf4j
@NoArgsConstructor
public class GenericSink extends HasParamMethodSinks implements MethodSinks{

    private SinkRule sinkRule;

    private RulesConfig rulesConfig;

    private static String[] filteredList = {"<java.io.PrintStream: void print", "<java.lang.StringBuilder: java.lang.StringBuilder append", "<java.lang.String: boolean equals"};

    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional= new HashMap<>();
        if (sinkRule != null){

            FunctionIdentifier functionIdentifier = sinkRule.getFunctionIdentifier();
            Sink sink = sinkRule.getSink();
            Element conditionalEle = sink.getConditional();
            // todo. Need to support complex conditional expressions
            if (conditionalEle != null && !rulesConfig.rulesConditionalSwitcher){
                log.info("the switcher of conditional expressions is currently off, skip temporarily. sink rule is : " + sinkRule.getRuleID());
                return;
            }
            String[] inArguments = sink.getInArguments();

            // todo. to be optimized （list to map）
            for (SootClass sootClass : Scene.v().getClasses()) {

                String invokeFullClassName = sootClass.getName();
                try{
                    String className = invokeFullClassName.substring(invokeFullClassName.lastIndexOf(".") + 1, invokeFullClassName.length());
                    String packageName = invokeFullClassName.substring(0, invokeFullClassName.lastIndexOf("."));

                    if (isMatched(packageName, functionIdentifier.getNamespaceName()) && isMatched(className, functionIdentifier.getClassName())) {
                        for(SootMethod sootMethod : sootClass.getMethods()) {

                            // todo. Manually filter some methods
                            if (filteredListMatched(sootMethod)) continue;

                            String methodName = sootMethod.getName();
                            if (methodName.equals("<init>")){
                                methodName = "init";
                            }

                            if(isMatched(methodName, functionIdentifier.getFunctionName())) {
                                sinkMethods.add(sootMethod);
                                conditional.put(sootMethod.getSignature(), conditionalEle);

                                List<Integer> inArgumenList = new LinkedList<>();
                                for (String inArgument : inArguments){
                                    if (inArgument.equals(Constants.ARG_THIS)){
                                        inArgumenList.add(-1);
                                    }else if (inArgument.contains(Constants.ARG_DOT)){
                                        //todo. need to deal with "..."
                                        int index = Integer.parseInt(inArgument.substring(0,1));
                                        inArgumenList.addAll(GenericUtil.getSeries(index, 10));
                                    }else{
                                        inArgumenList.add(Integer.valueOf(inArgument));
                                    }
                                }
                                sinkMethodsParams.put(sootMethod, inArgumenList);
                            }
                        }
                    }
                }catch (Exception ex){
//                    log.error("abnormal invokeFullClassName is : " + invokeFullClassName);
//                    log.error("error", ex);
                }

            }
        }
    }

    private static boolean filteredListMatched(SootMethod sootMethod) {
        boolean skip = false;
        for (String tag : filteredList){
            if (sootMethod.getSignature().contains(tag)){
                skip = true;
                break;
            }
        }
        return skip;
    }

    @Override
    public String getTypeName() {
        if (sinkRule == null){
            return "sink_type_generic";
        }
        return sinkRule.getVulCategory();
    }

    protected boolean isMatched(String name, Form form) {
        boolean nameMatched;
        String formPattern = form.getPattern();
        String formValue = form.getValue();
        if (!StringUtils.isEmpty(formPattern)){
            nameMatched = Pattern.matches(formPattern, name);
        }else{
            nameMatched = name.equals(formValue);
        }
        return nameMatched;
    }

}
