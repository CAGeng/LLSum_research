package detect.dataflow;

import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.DefaultDetector.MethodSummary;
import basic.util.StaticAnalyzeUtils.InvokeStmtUtil;
import basic.util.StaticAnalyzeUtils.Parameter;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;

import java.util.LinkedList;
import java.util.List;

public class SummaryReuse {

    // 返回是否成功复用
    public static boolean reuseSummary(TransformableNode transformableNode, SootMethod invokeMethod, MethodDescriptor descriptor, SootMethod sootMethod) {
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
            methodSummary.reuse_con += 1;
            return true;
        }
        return false;
    }


    // 分配式地复用
    // 对于cs【1，2】，分别用1和2匹配，这种复用总是成功
    public static void reuseSummaryDistributive(TransformableNode transformableNode, SootMethod invokeMethod, MethodDescriptor descriptor, SootMethod sootMethod) {
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
        List<Integer> rsTaints = methodSummary.getConditionalTaint(csTaintInds); // 先尝试非分配的复用
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
            methodSummary.reuse_con += 1;
        } else {
            // 此时分配地匹配
            for (int i = 0; i < csTaintInds.size(); i++) {
                List<Integer> csTaintInds_ = new LinkedList<>();
                csTaintInds_.add(csTaintInds.get(i));
                List<Integer> rsTaints_ = methodSummary.getConditionalTaint(csTaintInds_);
                if (rsTaints_ != null) {
                    for (int ind : rsTaints_) {
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
                                for (int i_ : csTaintInds_) {
                                    ValueBox argValueBox_cs = Parameter.getArgValueBox(transformableNode.node, i_);
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
                }
            }
            methodSummary.reuse_con += 1;
        }
    }

}
