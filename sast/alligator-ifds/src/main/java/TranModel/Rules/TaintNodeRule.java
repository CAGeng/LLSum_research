package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import TranModel.datastruct.TaintEdge;
import TranModel.datastruct.TaintNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.dataflow.fieldsensitive.AccessPathNode;
import fj.P;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JArrayRef;
import soot.jimple.internal.JInstanceFieldRef;
import soot.jimple.internal.JimpleLocal;

import java.util.List;

/*
    这个数据流规则比TaintSpreadRule更粗，但是会把数据流Summary保存在MethodDescriptor里；
 */
public class TaintNodeRule extends SuperRule implements Rule {

    public boolean field_sensitive = SpringContextUtils.getBean(GeneralConfig.class).fieldSensitiveSwitcher;

    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.context.method);

        process_assign(transformableNode, methodDescriptor);
        process_invoke(transformableNode, methodDescriptor);
        process_arrayAssign(transformableNode, methodDescriptor);
    }

    /*
        处理赋值语句
        a = taint, then taint a
     */
    private void process_assign(TransformableNode transformableNode, MethodDescriptor methodDescriptor) {

        Stmt stmt = (Stmt) transformableNode.node.unit;
        // field赋值，首先考虑是否采用field敏感策略，如果采用，不再进行后面的步骤
        if(field_sensitive) {
            if(stmt instanceof AssignStmt) {
                Value left = ((AssignStmt) stmt).getLeftOp();
                Value right = ((AssignStmt) stmt).getRightOp();
                //处理强制类型转换的情况
                if (right instanceof CastExpr) {
                    CastExpr castExpr = (CastExpr) right;
                    right = castExpr.getOp();
                }
                cloneAccessPath(methodDescriptor, right, left);
                if (right instanceof JInstanceFieldRef || left instanceof JInstanceFieldRef) {
                    processFieldSensitive(methodDescriptor, transformableNode);
                    return;
                }
            }
        }

        Value from = null;
        Value to = null;
        if (stmt instanceof AssignStmt) {
            Value left = ((AssignStmt) stmt).getLeftOp();
            Value right = ((AssignStmt) stmt).getRightOp();
            //处理强制类型转换的情况
            if (right instanceof CastExpr) {
                CastExpr castExpr = (CastExpr) right;
                right = castExpr.getOp();
            }

            if(right instanceof JInstanceFieldRef){
                // a = taint.field
                JInstanceFieldRef fieldRef = (JInstanceFieldRef) right;
                Value object = fieldRef.getBase();
                from = object;
                to = left;
            } else if(left instanceof  JInstanceFieldRef){
                // a.field = taint
                JInstanceFieldRef fieldRef = (JInstanceFieldRef) left;
                Value object = fieldRef.getBase();
                from = right;
                to = object;
            } else {
                from = right;
                to = left;
            }
        }

        taintSpread(from, to, methodDescriptor, transformableNode);
    }

    public boolean process_invoke_risky(TransformableNode transformableNode, MethodDescriptor methodDescriptor) {
        boolean spread = false;
        Stmt stmt = (Stmt) transformableNode.node.unit;
        // a = method(taint), then taint a
        if (stmt instanceof AssignStmt){
            if(transformableNode.containsInvoke()) {
                Value to = ((AssignStmt) stmt).getLeftOp();
                for(ValueBox valueBox : transformableNode.node.unit.getUseBoxes()) {
                    Value from = valueBox.getValue();
                    taintSpread(from, to, methodDescriptor, transformableNode);
                    spread = true;
                }
            }
        }
        if(transformableNode.containsInvoke()) {
            process_invoke(transformableNode, methodDescriptor);
        }
        return spread;
    }

    private void process_invoke(TransformableNode transformableNode, MethodDescriptor methodDescriptor) {
        Stmt stmt = (Stmt) transformableNode.node.unit;
        // e.g. interfaceinvoke $r1.<java.util.List: boolean add(java.lang.Object)>(r2)
        if (stmt.containsInvokeExpr()) {
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            if(invokeExpr instanceof InterfaceInvokeExpr){
                InterfaceInvokeExpr interfaceInvokeExpr = (InterfaceInvokeExpr) invokeExpr;
                if (invokeExpr.getMethod().getName().startsWith("add")) {
                    Value receiver = interfaceInvokeExpr.getBase(); // $r1
                    Value argument = interfaceInvokeExpr.getArg(0); // r2
                    taintSpread(argument, receiver, methodDescriptor, transformableNode);
                }
            }
            if(invokeExpr instanceof VirtualInvokeExpr){
                VirtualInvokeExpr interfaceInvokeExpr = (VirtualInvokeExpr) invokeExpr;
                if (invokeExpr.getMethod().getName().startsWith("add")) {
                    Value receiver = interfaceInvokeExpr.getBase(); // $r1
                    Value argument = interfaceInvokeExpr.getArg(0); // r2
                    taintSpread(argument, receiver, methodDescriptor, transformableNode);
                }
                if (invokeExpr.getMethod().getName().startsWith("read")) {
                    Value argument = interfaceInvokeExpr.getBase(); // $r1
                    for(ValueBox valueBox : transformableNode.node.unit.getDefBoxes()) {
                        Value receiver = valueBox.getValue(); // return
                        taintSpread(argument, receiver, methodDescriptor, transformableNode);
                    }
                }
            }
            if(invokeExpr instanceof SpecialInvokeExpr){
                SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
                // e.g. $r0.<java.net.URL: void <init>(java.lang.String)>(r1)
                if (invokeExpr.getMethod().getSignature().contains("void <init>(java.lang.String)")) {
                    Value receiver = specialInvokeExpr.getBase(); // $r1
                    Value argument = specialInvokeExpr.getArg(0); // r2
                    taintSpread(argument, receiver, methodDescriptor, transformableNode);
                }
                if (invokeExpr.getMethod().getSignature().contains("void <init>(byte[])")) {
                    Value receiver = specialInvokeExpr.getBase(); // $r1
                    Value argument = specialInvokeExpr.getArg(0); // r2
                    taintSpread(argument, receiver, methodDescriptor, transformableNode);
                }
                // specialinvoke $r3.<java.io.File: void <init>(java.io.File,java.lang.String)>($r5, $r6)
                if (invokeExpr.getMethod().getSignature().contains("void <init>(java.io.File,java.lang.String)")) {
                    Value receiver = specialInvokeExpr.getBase(); // $r1
                    Value argument0 = specialInvokeExpr.getArg(0); // r2
                    Value argument1 = specialInvokeExpr.getArg(1); // r2
                    taintSpread(argument0, receiver, methodDescriptor, transformableNode);
                    taintSpread(argument1, receiver, methodDescriptor, transformableNode);
                }

                if (invokeExpr.getMethod().getSubSignature().equals("void <init>(java.io.InputStream)")){
                    Value receiver = specialInvokeExpr.getBase(); // $r1
                    Value argument = specialInvokeExpr.getArg(0); // r2
                    taintSpread(argument, receiver, methodDescriptor, transformableNode);
                }
            }
        }

    }

    /*
        a = taint[i] then taint a
        a[i] = taint then taint a
     */
    private void process_arrayAssign(TransformableNode transformableNode, MethodDescriptor methodDescriptor){
        Stmt stmt = (Stmt) transformableNode.node.unit;
        if (stmt instanceof AssignStmt) {
            Value left = ((AssignStmt) stmt).getLeftOp();
            Value right = ((AssignStmt) stmt).getRightOp();
            Value from = right;
            Value to = left;
            if(left instanceof JArrayRef){
                to = ((JArrayRef) left).getBase();
            }
            if(right instanceof JArrayRef){
                from = ((JArrayRef) right).getBase();
            }
            taintSpread(from, to, methodDescriptor, transformableNode);
        }
    }


    @Override
    public String getName() {
        return this.getClass().getName();
    }
}
