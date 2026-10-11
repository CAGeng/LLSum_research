package basic.DefaultDetector;

import TranModel.TransformableNode;
import TranModel.datastruct.TaintNode;
import basic.cfg.CFG;
import basic.dataflow.fieldsensitive.AccessPathNode;
import basic.dataflow.fieldsensitive.AliasGraph;
import basic.util.StaticAnalyzeUtils.Parameter;
import intelligent.condition.CT;
import lombok.Getter;
import lombok.Setter;
import soot.SootMethod;
import soot.Type;
import soot.Value;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.*;

//import static Detector.DEBUG_METHOD;


@Getter
@Setter
public class MethodDescriptor extends DefaultMethodDescriptor {

    public CFG cfg = null;

    //////////////////////////////// 24-1-4 LLM Method Summary -sft ////////////////////////
    public MethodSummary methodSummary = new MethodSummary(this.sootMethod);

    //////////////////////////////// 23-12-7 field-sensitive -sft ////////////////////////
    public AliasGraph aliasGraph = new AliasGraph();

    //////////////////////////////// 24-8-13 alias -sft ////////////////////////
    public HashMap<String, String> aliasMapType = new HashMap<>();

    //////////////////////////////// 25-2-24 condition -sft ////////////////////////
    public HashMap<Integer, CT> value2CT = new HashMap<>();

    //////////////////////////////// 23-7-20 TaintNode -sft ////////////////////////
    // TaintNode是对Taint更粗的表示，TaintNode 总结了方法内的数据链路，每个TransformableNode里都保存了自己的0到多个TaintNode
    public List<TaintNode> allTaintNodes = new LinkedList<>();
    private HashMap<Value, TaintNode> val_m_taintNode = new HashMap<>();
    private boolean returnValueTainted = false;

    //////////////////////////////// 25-3-30 TaintNode -sft ////////////////////////
    public HashSet<TaintNode> sourceTaintNodes = new HashSet<>(); // 记录方法内产生的source节点，后续记录在detectcontext中

    public TransformableNode.BoundaryCheckState incomingBoundaryCheckState = TransformableNode.BoundaryCheckState.NO_CHECK;
    public TransformableNode.BoundaryCheckState outgoingBoundaryCheckState = TransformableNode.BoundaryCheckState.NO_CHECK;

    public void flushStates() {
        allTaintNodes.clear();
        val_m_taintNode.clear();
        aliasGraph = new AliasGraph();
        returnValueTainted = false;
    }

    public void addTaintNode(TaintNode taintNode) {
        val_m_taintNode.put(taintNode.value, taintNode);
        allTaintNodes.add(taintNode);
    }

    public TaintNode getTaintNodeByValue(Value value) {
        // field ref类型的，用字符串匹配的方式找
        if(value instanceof JInstanceFieldRef) {
            for(TaintNode taintNode : allTaintNodes) {
                if(taintNode.value == null) continue;
                if(taintNode.value.toString().equals(value.toString())) {
                    return taintNode;
                }
            }
        }
        if(!val_m_taintNode.containsKey(value)) return null;
        return val_m_taintNode.get(value);
    }

    public int getTaintNodeSize() {
        return allTaintNodes.size();
    }

    public void deleteTaintNode(TaintNode taintNode) {
        allTaintNodes.remove(taintNode);
        val_m_taintNode.entrySet().removeIf(entry -> entry.getValue() == taintNode);
    }

    /**
     * 查询输入的污点参数
     */
    public List<Integer> getInputTaintParamId() {
        List<Integer> ret = new LinkedList<>();
        for(TaintNode taintNode : allTaintNodes) {
            if(taintNode.paramInd != null) {
                ret.add(taintNode.paramInd);
            }
        }
        return ret;
    }

    public List<Integer> getTaintedParams() {

        if(this.cfg == null) {
            this.cfg = new CFG(this.sootMethod, true).buildCFG();
        }
        if(this.paramIdMapLocalValue == null) {
            this.paramIdMapLocalValue = Parameter.getParametersLocalValues(this.cfg);
        }

        List<Integer> ret = new LinkedList<>();
        for(int i = -1; i < this.sootMethod.getParameterCount(); i++) {
            if(this.paramIdMapLocalValue.containsKey(i)) {
                Value local = this.paramIdMapLocalValue.get(i);
                TaintNode taintNode = this.getTaintNodeByValue(local);
                if (taintNode != null) {
                    ret.add(i);
                } else {
                    AccessPathNode accessPathNode = this.aliasGraph.getNode(local);
                    if(accessPathNode != null) {
                        List<String> taintedAccessPaths = this.aliasGraph.getTaintedAccessPaths(accessPathNode);
                        if(!taintedAccessPaths.isEmpty()) {
                            ret.add(i);
                        }
                    }
                }
            }
        }
        return ret;
    }

    // 用于获得包含field敏感的数据流事实结果，用于得到条件摘要的初始和最终状态
    public String getDetailedDataflowFacts() {
        List<Integer> taintedParams = this.getTaintedParams();
        StringBuilder sb = new StringBuilder();
        for(int ind = -1; ind < this.sootMethod.getParameterCount(); ind++) {
            Value local = this.paramIdMapLocalValue.get(ind);
            AccessPathNode accessPathNode = this.aliasGraph.getNode(local);
            if(accessPathNode != null) {
                List<String> taintedAccessPaths = this.aliasGraph.getTaintedAccessPaths(accessPathNode);
                for(String taintedAccessPath : taintedAccessPaths) {
                    sb.append("param" + ind + " : " + taintedAccessPath + "\n");
                }
            } else {
                // 说明field全污染
                if(taintedParams.contains(ind)) {
                    sb.append("param" + ind + " : " + "field-all\n");
                }

            }
        }
        if(returnValueTainted) {
            sb.append("return\n");
        }
        return sb.toString();
    }

    public HashMap<Integer, Value> paramIdMapLocalValue = null;

    //////////////////////////////////  记录  /////////////////////////////////////////////

    public MethodDescriptor(SootMethod method) {
        super(method);
    }


    //获得所有有关这个Value的新增的taint，排除初始传入的taint
    public static HashMap<SootMethod, MethodDescriptor> methodMapDescriptor = new HashMap<>();
    public static MethodDescriptor getOrCreateDescriptor(SootMethod sootMethod){
        if(methodMapDescriptor.containsKey(sootMethod)){
            return methodMapDescriptor.get(sootMethod);
        }else{
            MethodDescriptor methodDescriptor = new MethodDescriptor(sootMethod);
            methodMapDescriptor.put(sootMethod, methodDescriptor);
            return methodDescriptor;
        }
    }

    /*
        刷新所有MethodDescriptor
     */
    public static void flushAllDescriptors() {
        methodMapDescriptor = new HashMap<>();

        methodMapDescriptor.putAll(basicMethodMapDescriptor);
    }

    public static HashMap<SootMethod, MethodDescriptor> basicMethodMapDescriptor = new HashMap<>();

    public static void loadPropagationRules() {
        // basicMethodMapDescriptor.put(sootmethod, propagationRuleToMethodDescriptor(sootmethod, csTaints, rsTaints));
    }

    public static MethodDescriptor propagationRuleToMethodDescriptor(SootMethod sootMethod, List<Integer> csTaints, List<Integer> rsTaints) {
        MethodDescriptor methodDescriptor = new MethodDescriptor(sootMethod);
        methodDescriptor.methodSummary = new MethodSummary(sootMethod);
        methodDescriptor.methodSummary.addConditionalTaint(csTaints, rsTaints);

        return methodDescriptor;
    }

    public static class ValueInfo{
        public Type type;
        public ValueInfo(Type t){
            type = t;
        }
        public ValueInfo(){}
    }

    //这里用来记录一些被长生命周期集合类扩增的Value
    public Set<Value> AppendValue = new HashSet<>();
    public boolean retExtens;

}
