package TranModel.Rules;

import TranModel.TransformableNode;
import TranModel.datastruct.PayloadStructure;
import TranModel.datastruct.SrcMessage;
import TranModel.datastruct.TaintEdge;
import TranModel.datastruct.TaintNode;
import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import app.importer.entity.Form;
import app.importer.entity.FunctionIdentifier;
import app.importer.entity.GenericRule;
import basic.DefaultDetector.MethodDescriptor;
import basic.dataflow.fieldsensitive.AccessPathNode;
import basic.dataflow.fieldsensitive.AliasGraph;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import soot.SootClass;
import soot.SootMethod;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: luca
 * Date: 2023/10/17
 * Description:
 */
@Slf4j
public class SuperRule {

    /*
    仅就Value，判断是否需要，如果需要，传播taintNode, 记录taintEdge
 */
    protected boolean taintSpread(Value from, Value to, MethodDescriptor methodDescriptor, TransformableNode transformableNode) {
        TaintNode taintNode = methodDescriptor.getTaintNodeByValue(from);
        if(taintNode != null) {
            if(TransformableNode.recordExplain) {
                transformableNode.explainMessage += "在这条语句中,污点从变量" + from + " 传播到变量 " + to + ";";
            }
            if(methodDescriptor.getTaintNodeByValue(to) == null) {
                boolean getField_success = false;
                if(taintNode.srcMessage != null && taintNode.srcMessage.isStructure) {
                    // 结构化数据的传播
                    getField_success = taintSpreadStructureData(from, to, methodDescriptor, transformableNode);
                }
                if(!getField_success){
                    TaintNode newTaintNode = new TaintNode(to, methodDescriptor.sootMethod, taintNode.getTaintFlags());
                    newTaintNode.srcMessage = taintNode.srcMessage;
                    taintNode.addSuccessor(newTaintNode, transformableNode);
                    methodDescriptor.addTaintNode(newTaintNode);
                }
                return true;
            }
        }
        return false;
    }

    protected boolean taintSpreadStructureData(Value from, Value to, MethodDescriptor methodDescriptor, TransformableNode transformableNode) {
        TaintNode taintNode = methodDescriptor.getTaintNodeByValue(from);
        Stmt stmt = (Stmt) transformableNode.node.unit;
        // e.g. a = user.getUsername()
        // 尝试匹配get-set方法
        String fieldName = null;
        if (stmt instanceof AssignStmt){
            if(transformableNode.containsInvoke()) {
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                String methodName = invokeExpr.getMethod().getName();
                if(methodName.startsWith("get") && methodName.length() > 3) {
                    fieldName = methodName.substring(3);
                    fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                }
            }
        }
        if (stmt instanceof AssignStmt) {
            Value left = ((AssignStmt) stmt).getLeftOp();
            Value right = ((AssignStmt) stmt).getRightOp();
            if(right instanceof JInstanceFieldRef){
                // a = taint.field
                JInstanceFieldRef fieldRef = (JInstanceFieldRef) right;
                fieldName = fieldRef.getField().getName();
            }
        }

        if(fieldName != null) {
            PayloadStructure payloadStructure = taintNode.srcMessage.payloadStructure;
            String newAccessPath = taintNode.srcMessage.accessPath + " -> " + fieldName;
            if (payloadStructure.accessPaths.contains(newAccessPath)) {
                // 成功匹配到新的accessPath，把它赋给左值
                TaintNode newTaintNode = new TaintNode(to, methodDescriptor.sootMethod, taintNode.getTaintFlags());
                newTaintNode.srcMessage = new SrcMessage(taintNode.srcMessage.entryParamId, taintNode.srcMessage.isStructure,
                        taintNode.srcMessage.payloadStructure, newAccessPath);
                taintNode.addSuccessor(newTaintNode, transformableNode);
                methodDescriptor.addTaintNode(newTaintNode);
                log.error("结构化的污点传播, field获取： " + newAccessPath);
                return true;
            }
        }
        return false;

    }

    protected boolean isMatched(String name, Form form) {
        boolean nameMatched;
        String formPattern = form.getPattern();
        String formValue = form.getValue();
        if (!StringUtils.isEmpty(form.getPattern())){
            nameMatched = Pattern.matches(formPattern, name);
        }else{
            nameMatched = name.equals(formValue);
        }
        return nameMatched;
    }

    protected void applyTaint(TransformableNode transformableNode, MethodDescriptor methodDescriptor, GenericRule rule){

        Stmt stmt = (Stmt) transformableNode.node.unit;

        if (stmt.containsInvokeExpr()) {
            //todo.Need to consider internal classes
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            SootMethod invokeExprSootMethod = invokeExpr.getMethod();
            String methodSignature = invokeExprSootMethod.getSignature();
            String invokeFullClassName = methodSignature.substring(1, methodSignature.indexOf(':'));

            String className = invokeFullClassName.substring(invokeFullClassName.lastIndexOf(".") + 1, invokeFullClassName.length());
            String packageName = invokeFullClassName.substring(0, invokeFullClassName.lastIndexOf("."));
            String methodName = invokeExprSootMethod.getName();

            // special case for constructor
            if (methodName.equals("<init>")){
                methodName = "init";
            }

            FunctionIdentifier functionIdentifier = rule.getFunctionIdentifier();
            // Match NamespaceName & ClassName & FunctionName
            if (isMatched(packageName, functionIdentifier.getNamespaceName()) && isMatched(className, functionIdentifier.getClassName()) && isMatched(methodName, functionIdentifier.getFunctionName())){
                //todo.Need for finer grained matching
                extracted(transformableNode, methodDescriptor, stmt, invokeExpr, methodSignature, functionIdentifier);
            }else{
                if (functionIdentifier.getApplyTo() != null && functionIdentifier.getApplyTo().isTagExtends()){
                    // todo. support extend
                    HashSet<SootClass> sootClasses =  ClassUtils.getAllSupers(invokeExpr.getMethod().getDeclaringClass());
                    for (SootClass sootClass : sootClasses){
                        if (isMatched(sootClass.getPackageName(), functionIdentifier.getNamespaceName()) && isMatched(sootClass.getName(), functionIdentifier.getClassName()) && isMatched(methodName, functionIdentifier.getFunctionName())){
                            //todo.Need for finer grained matching
                            extracted(transformableNode, methodDescriptor, stmt, invokeExpr, methodSignature, functionIdentifier);
                        }
                    }
                }
            }
        }
    }

    private void extracted(TransformableNode transformableNode, MethodDescriptor methodDescriptor, Stmt stmt, InvokeExpr invokeExpr, String methodSignature, FunctionIdentifier functionIdentifier) {
        String[] parameters = functionIdentifier.getParameters();
        Pattern pattern = Pattern.compile("\\((.*?)\\)");
        Matcher matcher = pattern.matcher(methodSignature);

        String argsStr = null;
        while (matcher.find()) {
            argsStr = matcher.group(1);
            break;
        }
        // Need to be matched with the parameters of the method
        if (parameters != null && StringUtils.isNotEmpty(argsStr)){
            String[] args = argsStr.split(",");
            if (Arrays.equals(parameters, args)){
                extendTaintNode(methodDescriptor, stmt, invokeExpr, transformableNode);
            }
            // Without setting parameters means matching all methods with the same name
        }else if(parameters == null){
            extendTaintNode(methodDescriptor, stmt, invokeExpr, transformableNode);
        }
    }

    /**
     * field敏感的算法
     * 例子：
     * (此时污点集合{b->data})
     *  1. a = b.field                         污点集合{b->data}
     *                             b
     *                            |
     *                         a(field)
     *  2. c = b.data                          污点集合{b->data, c}
     *                               b
     *                            |    \
     *                         a(field) c(data) <----------c
     *  3. a.data = c                          污点集合{b->data, c, a->data, b->field->data}
     *                               b
     *                            |    \
     *                         a(field) c(data)
     *                            |
     *                          data
     */
    // 返回是否成功apply这个传播规则
    protected boolean processFieldSensitive(MethodDescriptor methodDescriptor, TransformableNode transformableNode) {
        Stmt stmt = (Stmt) transformableNode.node.unit;
        // a = b.field
        if (stmt instanceof AssignStmt) {
            Value left = ((AssignStmt) stmt).getLeftOp();
            Value right = ((AssignStmt) stmt).getRightOp();
            if(right instanceof JInstanceFieldRef){
                JInstanceFieldRef fieldRef = (JInstanceFieldRef) right;
                // a = b.field
                Value bValue = fieldRef.getBase();
                Value aValue = left;

                // 找b的node, 没有就创建
                if(methodDescriptor.aliasGraph.getNode(bValue) == null) {
                    methodDescriptor.aliasGraph.addRoot(methodDescriptor.aliasGraph.createNode(null, bValue));
                }
                AccessPathNode bNode = methodDescriptor.aliasGraph.getNode(bValue);

                // 找b.field的node，有就让aValue指向他，没有就创建并让aValue指向他
                AccessPathNode bFieldNode = methodDescriptor.aliasGraph.getNode(bValue, fieldRef.getField());
                if(bFieldNode != null) {
                    // System.out.println("1: 找到了bfieldNode");
                    // System.out.println(bFieldNode.isTaint);
                    methodDescriptor.aliasGraph.addPointTo(aValue, bFieldNode);
                } else {
                    // System.out.println("未找到bfieldNode");
                    // 在createNode时默认添加了aValue对这个accessNode的指向关系
                    bFieldNode = methodDescriptor.aliasGraph.createNode(fieldRef.getField(), aValue);
                    bNode.addChild(bFieldNode);
                }

                // 传播污点
                TaintNode rightTaintNode = methodDescriptor.getTaintNodeByValue(right);
                // a = b.field, 也考虑整个b被污染情况
                TaintNode bTaintNode = methodDescriptor.getTaintNodeByValue(bValue);
                if(rightTaintNode != null || bFieldNode.isTaint || bTaintNode != null) {
                    // System.out.println("传播污点");
                    // right可能没在污点集合，所以需要预先帮right找一下污点的来源（为了后面找数据链路）
                    if(rightTaintNode == null) {
                        rightTaintNode = new TaintNode(right, methodDescriptor.sootMethod);
                        methodDescriptor.addTaintNode(rightTaintNode);
                        for(Value aliasValue : bFieldNode.values) {
                            // 所有别名中应该包含能够上溯的value，它是使得这个fieldNode被污染的value，污染语句是以这个别名为左值的
                            // System.out.println("发现别名：" + aliasValue);
                            TaintNode aliasTaintNode = methodDescriptor.getTaintNodeByValue(aliasValue);
                            if(aliasTaintNode != null) {
                                // System.out.println("找到别名的taintNode");
                                for(TaintEdge edge : aliasTaintNode.ins) {
                                    TaintNode fromNode = edge.from;
                                    fromNode.addSuccessor(rightTaintNode, edge.transformableNode);
                                }
                            }
                        }
                    }
                    taintSpread(right, left, methodDescriptor, transformableNode); // 例子中的污点集合{b->data, c}
                }

                return true;
            }
            if(left instanceof JInstanceFieldRef) {
                JInstanceFieldRef fieldRef = (JInstanceFieldRef) left;
                // b.field = a
                Value bValue = fieldRef.getBase();

                // 找b的node, 没有就创建
                if(methodDescriptor.aliasGraph.getNode(bValue) == null) {
                    methodDescriptor.aliasGraph.addRoot(methodDescriptor.aliasGraph.createNode(null, bValue));
                }
                AccessPathNode bNode = methodDescriptor.aliasGraph.getNode(bValue);
                // 建b.field的node
                AccessPathNode bFieldNode = methodDescriptor.aliasGraph.createNode(fieldRef.getField(), null);
                bNode.addChild(bFieldNode);
                // System.out.println("建立bfieldNode: " + fieldRef.getField());

                // 传播污点
                TaintNode rightTaintNode = methodDescriptor.getTaintNodeByValue(right);
                if(rightTaintNode != null) {
                    // System.out.println("为新建立的bfieldNode传播污点");
                    boolean success = taintSpread(right, left, methodDescriptor, transformableNode); // 例子中的污点集合{b->data, c}
                    // System.out.println("传播成功: " + success + " 到： " + left);
                    // System.out.println(left.getClass());
                    bFieldNode.isTaint = true;
                }
                return true;
            }
        }
        return false;

    }
    protected void cloneAccessPath(MethodDescriptor methodDescriptor, Value from, Value to) {
        AccessPathNode accessPathNodeFrom = methodDescriptor.aliasGraph.getNode(from);
        if(accessPathNodeFrom == null) {
            return;
        }
        try {
            AccessPathNode clonedAccessPathNode = accessPathNodeFrom.recurseClone();
            clonedAccessPathNode.values.add(to);
            methodDescriptor.aliasGraph.addRoot(clonedAccessPathNode);
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    protected void extendTaintNode(MethodDescriptor methodDescriptor, Stmt stmt, InvokeExpr invokeExpr,TransformableNode transformableNode){
    }
}
