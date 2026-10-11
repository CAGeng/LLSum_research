package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JInstanceFieldRef;

/*
    这个数据流规则比TaintSpreadRule更粗，但是会把数据流Summary保存在MethodDescriptor里；
 */
public class AliasTypeRule extends SuperRule implements Rule {

    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);

        process_new(transformableNode, methodDescriptor);
    }

    /*
        处理赋值语句
        a = taint, then taint a
     */
    private void process_new(TransformableNode transformableNode, MethodDescriptor methodDescriptor) {

        Stmt stmt = (Stmt) transformableNode.node.unit;

        Value from = null;
        Value to = null;
        if (stmt instanceof AssignStmt) {
            Value left = ((AssignStmt) stmt).getLeftOp();
            Value right = ((AssignStmt) stmt).getRightOp();
            //处理强制类型转换的情况
            if (right instanceof NewExpr) {
                NewExpr newExpr = (NewExpr) right;
                String type = newExpr.getType().toString();
                methodDescriptor.aliasMapType.put(left.toString(), type);
//                System.out.println("AliasTypeRule: " + left.toString() + " -> " + type);
            }
        }

    }


    @Override
    public String getName() {
        return this.getClass().getName();
    }
}
