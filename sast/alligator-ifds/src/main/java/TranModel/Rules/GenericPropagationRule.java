package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import app.importer.entity.PropagationRule;
import app.utils.Constants;
import app.utils.GenericUtil;
import basic.DefaultDetector.MethodDescriptor;
import basic.DefaultDetector.MethodSummary;
import basic.dataflow.fieldsensitive.AccessPathNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * User: luca
 * Date: 2023/10/19
 * Description: GenericPropagationRule for the import of various propagation rules
 */

@Component
@Slf4j
@Getter
@Setter
public class GenericPropagationRule extends SuperRule implements Rule {

    /**
     * propagationRule
     */
    private PropagationRule propagationRule;

    private GeneralConfig generalConfig;

    @Autowired
    public GenericPropagationRule(GeneralConfig generalConfig){
        this.generalConfig = generalConfig;
    }

    public GenericPropagationRule(){
    }


    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);
        if (propagationRule == null){
            return;
        }
        applyTaint(transformableNode, methodDescriptor,propagationRule);
    }


    public void extendTaintNode(MethodDescriptor methodDescriptor, Stmt stmt, InvokeExpr invokeExpr,TransformableNode transformableNode) {
        // rich summary
        if (generalConfig.conditionSummarySwitcher){
            richSummary(invokeExpr);
        }

        // taint propagation
        String[] outArguments = propagationRule.getOutArguments();
        String[] inArguments = propagationRule.getInArguments();

        for (String outArgument : outArguments){
            if (outArgument.equals(Constants.ARG_RETURN)){
                if (stmt instanceof AssignStmt){
                    for (String inArgument : inArguments){
                        if(inArgument.equals(Constants.ARG_THIS)){
                            /**
                             * String testName = name.strip();
                             * System.out.println(testName);
                             *
                             * <InArguments>this</InArguments>
                             * <OutArguments>return</OutArguments>
                             *
                             * If "name" is taintNode, then taint "testName"
                             */
                            Value from = null;
                            Value to = ((AssignStmt) stmt).getLeftOp();
                            if(invokeExpr instanceof VirtualInvokeExpr){
                                VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                                from = virtualInvokeExpr.getBase();
                            }else if (invokeExpr instanceof SpecialInvokeExpr){
                                SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
                                from = specialInvokeExpr.getBase();
                            }
                            if (generalConfig.fieldSensitiveSwitcher){
                                cloneAccessPath(methodDescriptor, from, to);
                            }
                            taintSpread(from, to, methodDescriptor, transformableNode);
                        }else if (inArgument.contains(Constants.ARG_DOT)){
                            /**
                             * todo.
                             * 0...
                             */
                            //log.info("will support '...' ");
                        }else if (inArgument.contains(Constants.ARG_DASH)){
                            int index = Integer.valueOf(inArgument.split(Constants.ARG_DASH)[0]);
                            Value to = ((AssignStmt) stmt).getLeftOp();
                            int i = -2;
                            for(ValueBox valueBox : transformableNode.node.unit.getUseBoxes()) {
                                if (i++ == index){
                                    Value from = valueBox.getValue();
                                    cloneAccessPath(methodDescriptor, from, to);
                                }
                            }
                        }else{
                            /**
                             * URI uri = URI.create(name);
                             *
                             * <InArguments>0</InArguments>
                             * <OutArguments>return</OutArguments>
                             *
                             * If "name" is taintNode, then taint "uri"
                             */
                            int index = Integer.valueOf(inArgument);
                            Value to = ((AssignStmt) stmt).getLeftOp();
                            int i = -1;
                            if(invokeExpr instanceof InterfaceInvokeExpr){
                                i = -2;
                            }
                            for(ValueBox valueBox : transformableNode.node.unit.getUseBoxes()) {
                                if (i++ == index){
                                    Value from = valueBox.getValue();
                                    if (generalConfig.fieldSensitiveSwitcher){
                                        cloneAccessPath(methodDescriptor, from, to);
                                    }
                                    taintSpread(from, to, methodDescriptor, transformableNode);
                                }
                            }
                        }
                    }
                }
            }else if(outArgument.equals(Constants.ARG_THIS)){
                for (String inArgument : inArguments){
                    if (inArgument.equals(Constants.ARG_THIS)){
                        // todo.
                    }else if (inArgument.contains(Constants.ARG_DOT)){
                        // todo.
                    }else{
                        /**
                         * Properties properties = new Properties();
                         * Reader re = new FileReader("pom.xml");
                         * properties.load(re);
                         *
                         * "re" is tainted, then taint "properties"
                         */
                        Value newValue = null;
                        if(invokeExpr instanceof VirtualInvokeExpr){
                            VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                            newValue = virtualInvokeExpr.getBase();
                        }else if (invokeExpr instanceof SpecialInvokeExpr){
                            SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
                            newValue = specialInvokeExpr.getBase();
                        }

                        int index = Integer.valueOf(inArgument);
                        int i = -1;
                        if(invokeExpr instanceof InterfaceInvokeExpr){
                            i = -2;
                        }
                        for(ValueBox valueBox : transformableNode.node.unit.getUseBoxes()) {
                            if (i++ == index){
                                Value from = valueBox.getValue();
                                taintSpread(from, newValue, methodDescriptor, transformableNode);
                            }
                        }
                    }
                }
            }else{
                log.info("will support this case when outArgument is 'other' : " + propagationRule.getRuleId());
            }
        }
    }


    @Override
    public String getName() {
        return null;
    }


    /**
     * rich summary based on the propagation rule
     *
     * @param invokeExpr
     */
    public void richSummary(InvokeExpr invokeExpr){
        SootMethod invokeMethod = invokeExpr.getMethod();
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(invokeMethod);
        MethodSummary methodSummary = methodDescriptor.methodSummary;

        String[] inArguments = propagationRule.getInArguments();
        String[] outArguments = propagationRule.getOutArguments();

        List<Integer> csTaintInds = getTaintInds(inArguments);
        List<Integer> rsTaints = methodSummary.getConditionalTaint(csTaintInds);

        List<Integer> retTaintInds = getTaintInds(outArguments);
        if (CollectionUtils.isEmpty(rsTaints)){
            methodSummary.addConditionalTaint(csTaintInds, retTaintInds);
//            log.info("rich summary : " + invokeMethod.getName() + " : " + methodSummary.getConditionalTaint().toString());
            methodSummary.tags.add(Constants.TAG_SAST);
        }
    }

    private List<Integer> getTaintInds(String[] arguments) {
        List<Integer> taintInds = new ArrayList<>();
        for (String s : arguments){
            if(s.equals(Constants.ARG_THIS)){
                /**
                 * this
                 */
                taintInds.add(-1);
            }else if (s.contains(Constants.ARG_DOT)){
                /**
                 * 0...
                 */
                int index = Integer.parseInt(s.substring(0,1));
                List<Integer> series = GenericUtil.getSeries(index, 10);
                taintInds.addAll(series);
            }else if (s.equals(Constants.ARG_RETURN)){
                /**
                 * return
                 */
                taintInds.add(-2);
            }else if (s.contains(Constants.ARG_DASH)){
                /**
                 * 0-d
                 */
            }else{
                /**
                 * 0,1
                 */
                try {
                    taintInds.add(Integer.valueOf(s));
                } catch (NumberFormatException e){
                    log.error("getTaintInds : " + s + " : " + e.getMessage());
                }
            }
        }
        return taintInds;
    }

}
