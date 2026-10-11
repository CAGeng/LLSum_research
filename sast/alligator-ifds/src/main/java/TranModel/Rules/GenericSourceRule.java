package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import app.importer.entity.SourceRule;
import app.utils.Constants;
import basic.DefaultDetector.MethodDescriptor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Value;
import soot.jimple.*;

/**
 * User: luca
 * Date: 2023/10/16
 * Description: GenericSourceRule for the import of various source rules
 */

@Component
@Slf4j
@Getter
@Setter
public class GenericSourceRule extends SuperRule implements Rule {

    /**
     * sourceRule
     */
    private SourceRule sourceRule;

    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);
        if (sourceRule == null){
            return;
        }
        applyTaint(transformableNode, methodDescriptor, sourceRule);
    }

    public void extendTaintNode(MethodDescriptor methodDescriptor, Stmt stmt, InvokeExpr invokeExpr,TransformableNode transformableNode) {
        String[] outArguments = sourceRule.getOutArguments();
        for (String outArgument : outArguments){
            if (outArgument.equals(Constants.ARG_RETURN)){
                /**
                 * ZipEntry zipEntry = new ZipEntry("");
                 * String name = zipEntry.getName();
                 * System.out.println(name);
                 *
                 * taint "name"
                 */
                if (stmt instanceof AssignStmt){
                    Value newValue = ((AssignStmt) stmt).getLeftOp();
                    addTaintNode(methodDescriptor, newValue);
                }
            }else if (outArgument.equals(Constants.ARG_THIS)){
                /**
                 * Properties properties = new Properties();
                 * Reader re = new FileReader("pom.xml");
                 * properties.load(re);
                 *
                 * taint "properties"
                 */
                Value newValue = null;
                if(invokeExpr instanceof VirtualInvokeExpr){
                    VirtualInvokeExpr virtualInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                    newValue = virtualInvokeExpr.getBase();
                }else if (invokeExpr instanceof SpecialInvokeExpr){
                    SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
                    newValue = specialInvokeExpr.getBase();
                }
                addTaintNode(methodDescriptor, newValue);
            }else{
                /**
                 * Reader reader = new FileReader("pom.xml");
                 * char[] bs = {};
                 * int res2 = reader.read(bs, 1, 1);
                 * System.out.println(res2);
                 *
                 * taint "bs"
                 */
                Value newValue = invokeExpr.getArg(Integer.valueOf(outArgument));
                addTaintNode(methodDescriptor, newValue);
            }
        }
    }

    /**
     *
     * @param methodDescriptor
     * @param receiver
     */
    private void addTaintNode(MethodDescriptor methodDescriptor, Value receiver) {
        if(methodDescriptor.getTaintNodeByValue(receiver) == null) {
            TaintNode newTaintNode = new TaintNode(receiver, methodDescriptor.sootMethod, sourceRule.getTaintFlags());
            methodDescriptor.addTaintNode(newTaintNode);
            methodDescriptor.sourceTaintNodes.add(newTaintNode);
        }
    }

    @Override
    public String getName() {
        return null;
    }

}
