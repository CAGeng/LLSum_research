package detect.unit.instrument;

import TranModel.TranUtil;
import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import TranModel.datastruct.SrcMessage;
import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.util.Pair;
import basic.util.StaticAnalyzeUtils.Parameter;
import detect.dataflow.DataFlowTrack;
import detect.knowledge.Knows;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class InstrumentUtil {

    public static Pair<Integer, Integer> getStartAndEndLine(SootMethod sootMethod) {
        int startLine = -1, endLine = -1;
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        List<TransformableNode> topologicalOrderedTNodesFromCFG = TranUtil.getTopologicalOrderedTNodesFromCFG(cfg);
        for(TransformableNode tNode : topologicalOrderedTNodesFromCFG) {
            int unitLine = tNode.node.unit.getJavaSourceStartLineNumber();
            if(startLine == -1 && unitLine > 0) {
                startLine = unitLine;
            }
            if(unitLine > 0 && unitLine < startLine) {
                startLine = unitLine;
            }
            if(unitLine > endLine) {
                endLine = unitLine;
            }
        }
        return new Pair<>(startLine - 2, endLine + 2);
    }

    public static Pair<String, Integer> getUsedSite(TransformableNode tfNode) {
        return new Pair<>(tfNode.context.sootClass.getName(), getLineNumberByUnit(tfNode.node.unit));
    }

    public static Integer getLineNumberByUnit(Unit unit) {
        return unit.getJavaSourceStartLineNumber();
    }

    public static void recordUsedFields(TransformableNode tfNode, MethodDescriptor descriptor,
                                        DataFlowTrack.DataFlowResult dataFlowResult) {
        HashMap<SrcMessage, HashSet<Pair<String, Integer>>> ret = extractUsedFields(tfNode, descriptor);
        // todo: bfei 这里有ConcurrentModificationException
        try {
            for (SrcMessage srcMessage: ret.keySet()) {
                for (Pair<String, Integer> pair: ret.get(srcMessage)) {
                    String inClassName = pair.getKey();
                    Integer site = pair.getValue();
                    for (UsedSiteRecord usedSiteRecord : dataFlowResult.usedSiteRecords) {
                        if (usedSiteRecord.inClassName.equals(inClassName)
                                && usedSiteRecord.site.equals(site)) {
                            usedSiteRecord.srcMessages.add(srcMessage);
                            break;
                        }
                    }

                    UsedSiteRecord usedSiteRecord = new UsedSiteRecord();
                    usedSiteRecord.inClassName = inClassName;
                    usedSiteRecord.site = site;
                    usedSiteRecord.srcMessages.add(srcMessage);
                    dataFlowResult.usedSiteRecords.add(usedSiteRecord);
                }
            }
        } catch (Exception e) {
            System.out.println("todo: bfei");
        }

    }

    public static HashMap<SrcMessage, HashSet<Pair<String, Integer>>> extractUsedFields(TransformableNode tfNode, MethodDescriptor descriptor) {
        HashMap<SrcMessage, HashSet<Pair<String,Integer>>> ret = new HashMap<>();
        Stmt stmt = (Stmt) tfNode.node.unit;

        if (stmt instanceof AssignStmt){
            for (ValueBox valueBox : stmt.getUseAndDefBoxes()){
                addTaintSource(tfNode, valueBox, descriptor, ret);
            }
        }else if (stmt.containsInvokeExpr()){
            InvokeExpr invokeExpr = stmt.getInvokeExpr();
            ValueBox thisValueBox = Parameter.getThisValueBox(tfNode.node);
            if (thisValueBox != null){
                addTaintSource(tfNode, thisValueBox, descriptor, ret);
            }
            for (int ind = 0; ind < invokeExpr.getArgCount(); ind++) {
                addTaintSource(tfNode, invokeExpr.getArgBox(ind), descriptor,ret);
            }
        }

        return ret;
    }

    public static void addTaintSource(TransformableNode tfNode,
                                ValueBox valueBox,
                                MethodDescriptor descriptor,
                                HashMap<SrcMessage,
                                        HashSet<Pair<String, Integer>>> ret) {
        if (valueBox == null) return ;

        TaintNode taintNode = descriptor.getTaintNodeByValue(valueBox.getValue());
        if (taintNode != null){
            if (!ret.containsKey(taintNode.srcMessage))
                ret.put(taintNode.srcMessage, new HashSet<>());
            ret.get(taintNode.srcMessage).add(InstrumentUtil.getUsedSite(tfNode));
        }
    }


    public static void recordIfStmtAndMethodForInst(DataFlowTrack dataFlowTrack, Instruments instruments){
        for (ConditionNode conditionNode: dataFlowTrack.dataFlowResult.conditionNodes){
            if (conditionNode.basic){
                dataFlowTrack.dataFlowResult.conditionHashCodes.add(conditionNode.getHashCode());
            }
            SootClass sootClass = conditionNode.conditionNode.context.sootClass;
            SootMethod sootMethod = conditionNode.conditionNode.method;
            HashSet<IfStmtInstRecord> ifStmtInstRecords = instruments.classIfStmtsMap.get(sootClass.getName());
            HashSet<Integer> hashCodesRecord = new HashSet<>();
            if (ifStmtInstRecords != null) {
                for (IfStmtInstRecord ifStmtRecord : ifStmtInstRecords) {
                    hashCodesRecord.add(ifStmtRecord.hashCode);
                }
            }

            int hashCode = conditionNode.ifStmt != null?
                    conditionNode.ifStmt.hashCode(): conditionNode.tfNode.node.unit.hashCode();
            if (hashCodesRecord.contains(hashCode))
                continue;

            HashSet<IfStmtInstRecord> ifStmtRecords = new HashSet<>();

//            HashMap<IfStmt, Integer> ifStmtLineNumberMap = getLineNumberOfIfStmts(sootMethod);
            HashMap<IfStmt, Node> ifStmtNodeMap = getIfStmtNodeMap(sootMethod);

            IfStmtInstRecord ifStmtRecord = new IfStmtInstRecord();
            ifStmtRecord.setClassName(sootClass.getName());
            ifStmtRecord.setMethodSubSig(conditionNode.methodSig);
            ifStmtRecord.setMethodName(sootMethod.getName());
            ifStmtRecord.setLineNumber(conditionNode.usedSite.getValue());
            if (conditionNode.ifStmt != null) {
//                ifStmtRecord.setLineNumber(ifStmtLineNumberMap.get(conditionNode.ifStmt));
                ifStmtRecord.setHashCode(conditionNode.ifStmt.hashCode());
            }
            else {
                ifStmtRecord.setHashCode(conditionNode.tfNode.node.unit.hashCode());
            }


            ifStmtRecord.setBasic(false);

            // 记录后继
            Node node = conditionNode.tfNode.node;
            HashSet<Integer> successes = new HashSet<>();
            for (Node success : node.successorNodes){
                successes.add(getLineNumberByUnit(success.unit));
            }
            ifStmtRecord.setSuccessor(successes);

            ifStmtRecords.add(ifStmtRecord);

            if (instruments.getClassIfStmtsMap().containsKey(sootClass.getName())){
                instruments.getClassIfStmtsMap().get(sootClass.getName()).addAll(ifStmtRecords);
            }else {
                instruments.getClassIfStmtsMap().put(sootClass.getName(), ifStmtRecords);
            }
        }

        for (SootMethod sootMethod: dataFlowTrack.callStack){
            SootClass sootClass = sootMethod.getDeclaringClass();
            HashSet<MethodInstRecord> methodInstRecords = instruments.getClassMethodsMap().get(sootClass.getName());
            HashSet<String> methodSigs = new HashSet<>();

            if (methodInstRecords != null) {
                for (MethodInstRecord methodInstRecord : methodInstRecords)
                    methodSigs.add(methodInstRecord.sig);
            }
            if (!methodSigs.contains(sootMethod.getSignature())) {
                // 记录 methods 信息
                MethodInstRecord methodInstRecord = new MethodInstRecord();
                methodInstRecord.setClassName(sootClass.getName());
                methodInstRecord.setSubSig(sootMethod.getSubSignature());
                methodInstRecord.setSig(sootMethod.getSignature());
                methodInstRecord.setName(sootMethod.getName());
                methodInstRecord.setHashCode(sootMethod.getSignature().hashCode());

                if (instruments.getClassMethodsMap().containsKey(sootClass.getName())) {
                    instruments.getClassMethodsMap().get(sootClass.getName()).add(methodInstRecord);
                } else {
                    HashSet<MethodInstRecord> toAdd = new HashSet<>();
                    toAdd.add(methodInstRecord);
                    instruments.getClassMethodsMap().put(sootClass.getName(), toAdd);
                }
            }
        }
    }

    public static HashMap<IfStmt, Integer> getLineNumberOfIfStmts(SootMethod sootMethod){
        HashMap<IfStmt, Integer> ifStmtHashCodeMap = new HashMap<>();
        List<IfStmt> ifStmts = new ArrayList<>();
        if (!sootMethod.hasActiveBody())
            return ifStmtHashCodeMap;

        for (Unit unit: sootMethod.retrieveActiveBody().getUnits()){
            if (unit instanceof IfStmt)
                ifStmts.add((IfStmt) unit);
        }

        for (IfStmt ifStmt: ifStmts){
            ifStmtHashCodeMap.put(ifStmt, getLineNumberByUnit(ifStmt));
        }

        return ifStmtHashCodeMap;
    }

    public static HashMap<IfStmt, Node> getIfStmtNodeMap(SootMethod sootMethod){
        CFG cfg = new CFG(sootMethod, true);
        cfg.buildCFG();

        HashMap<IfStmt, Node> ret = new HashMap<>();
        for (Node node: cfg.allNodes.values()){
            Unit unit = node.unit;
            if (unit instanceof IfStmt)
                ret.put((IfStmt) unit, node);
        }
        return ret;
    }

    public static void recordSink(Knows knows, Instruments instruments){
        knows.getCollection().getMethodSinksMap().forEach((key, methodSinks) -> {
            HashMap<SootMethod, List<Integer>> sinkMethodsParams = methodSinks.sinkMethodsParams();
            for (SootMethod sootMethod: sinkMethodsParams.keySet()){
                List<Integer> pollutedParams = sinkMethodsParams.get(sootMethod);
                setSinkInstRecord(sootMethod, true, pollutedParams, instruments);
            }
        });
    }

    public static void setSinkInstRecord(SootMethod sootMethod, boolean flag, List<Integer> pollutedParams, Instruments instruments){
        SinkInstRecord sinkInstRecord = new SinkInstRecord();

        MethodInstRecord methodInstRecord = new MethodInstRecord();
        methodInstRecord.setName(sootMethod.getName());
        methodInstRecord.setSubSig(sootMethod.getSubSignature());
        methodInstRecord.setSig(sootMethod.getSignature());
        methodInstRecord.setClassName(sootMethod.getDeclaringClass().getName());
        methodInstRecord.setHashCode(sootMethod.getSignature().hashCode());
        sinkInstRecord.setMethodInstRecord(methodInstRecord);

        for (Integer paramInd: pollutedParams)
            sinkInstRecord.getPollutedParams().add(paramInd);
        sinkInstRecord.setFlag(flag);

        instruments.getSinkRecords().add(sinkInstRecord);
    }

}
