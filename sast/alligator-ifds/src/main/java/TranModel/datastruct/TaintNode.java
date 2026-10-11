package TranModel.datastruct;

import TranModel.TransformableNode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import soot.SootMethod;
import soot.Value;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
public class TaintNode {

    @JsonIgnore
    public Value value;

    @JsonIgnore
    public SootMethod inMethod;

    public SrcMessage srcMessage;

    public String assignUnit;

    @JsonIgnore
    private String[] taintFlags;

    private HashSet<String> taintFlags_2; // sft

    public String getValueStr() {
        if (value == null) {
            return "空value, 应该是占位，可能情况：ai";
        }
        return value.toString();
    }

    public String getMethodStr() {
        return inMethod.toString();
    }

    public String getTypeStr() {
        if (value == null) {
            return "空value, 应该是占位，可能情况：ai";
        }
        return value.getType().toString();
    }

    public String getParamInd() {
        if(paramInd == null) {
            return "null";
        } else {
            return paramInd.toString();
        }
    }

    @JsonIgnore
    public List<TaintEdge> outs = new LinkedList<>();

    @JsonIgnore
    public List<TaintEdge> ins = new LinkedList<>();

    // 如果是方法参数对应的value，设置它
    public Integer paramInd = null;

    public TaintNode(Value va, SootMethod sootMethod) {
        value = va;
        inMethod = sootMethod;
    }

    public TaintNode(Value va, SootMethod sootMethod, String[] taintFlags) {
        this.value = va;
        this.inMethod = sootMethod;
        this.taintFlags = taintFlags;
    }

    public void addSuccessor(TaintNode outNode, TransformableNode assignNode) {
        if(outNode.srcMessage == null) {
            outNode.srcMessage = this.srcMessage;
        }

        outNode.taintFlags_2 = this.taintFlags_2;

        TaintEdge edge = new TaintEdge(this, outNode, assignNode);
        this.outs.add(edge);
        outNode.ins.add(edge);
        if (assignNode != null)
            outNode.assignUnit = assignNode.toString();
    }

//    @Override
    public String toString2() {
        StringBuilder stringBuilder = new StringBuilder();
        if(paramInd != null) {
            stringBuilder.append("[param-index] ").append(paramInd);
            stringBuilder.append(" || ");
        }
        String valueType = value != null ? value.getType().toString() : "null";
        stringBuilder.append("[value-type] ").append(valueType);
        stringBuilder.append(" || ");
        stringBuilder.append("[value] ").append(value);
        stringBuilder.append(" || ");
        stringBuilder.append("[method] ").append(inMethod);
        stringBuilder.append(" || ");
        stringBuilder.append("[out-size] ").append(outs.size());
        stringBuilder.append(" || ");
        stringBuilder.append("[in-size] ").append(ins.size());
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("{ ");
//        if(paramInd != null) {
//            stringBuilder.append("[param-index] ").append(paramInd);
//            stringBuilder.append(" || ");
//        }
        String valueType = value != null ? value.getType().toString() : "null";
        stringBuilder.append("[value-type] ").append(valueType);
        stringBuilder.append(" || ");
        stringBuilder.append("[value] ").append(value);
//        stringBuilder.append(" || ");
//        stringBuilder.append("[out-size] ").append(outs.size());
//        stringBuilder.append(" || ");
//        stringBuilder.append("[in-size] ").append(ins.size());
        stringBuilder.append(" }");
        return stringBuilder.toString();
    }

}
