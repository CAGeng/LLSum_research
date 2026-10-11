package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.LinkedList;
import java.util.List;

public class TestcaseSourceRule implements Rule{

    public static List<TaintNode> testSources = new LinkedList<>();
    public static List<TaintNode> testSinks = new LinkedList<>();

    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);

        Stmt stmt = (Stmt) transformableNode.node.unit;
        if (stmt.containsInvokeExpr()) {
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            if(invokeExpr.getMethod().getSignature().equals("<com.l4yn3.microserviceseclab.controller.IndexController: java.lang.String source()>") ||
                    invokeExpr.getMethod().getSignature().equals("<com.l4yn3.microserviceseclab.controller.WhpController: java.lang.String source()>") ||
                    invokeExpr.getMethod().getSignature().equals("<com.l4yn3.microserviceseclab.controller.common.Dataflow: java.lang.String source()>")){
                if(stmt instanceof AssignStmt) {
                    Value to = ((AssignStmt) stmt).getLeftOp();
                    TaintNode source = new TaintNode(to, methodDescriptor.sootMethod);
                    methodDescriptor.addTaintNode(source);
                    testSources.add(source);
                }
            }
        }
    }

    @Override
    public String getName() {
        return null;
    }
}
