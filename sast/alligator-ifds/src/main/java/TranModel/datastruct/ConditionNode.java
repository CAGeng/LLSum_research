package TranModel.datastruct;

import TranModel.Symbol.Comparison;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.Node;
import basic.util.Pair;
import com.fasterxml.jackson.annotation.JsonIgnore;
import detect.unit.instrument.InstrumentUtil;
import intelligent.condition.CT;
import soot.Value;
import soot.ValueBox;
import soot.jimple.Constant;
import soot.jimple.IfStmt;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static basic.dataflow.DataFlow.findAllDefUnitAffectThisValue;

public class ConditionNode {
    public boolean badConditionFlag = false; // 如果这个标签为true，则这个condition是包含throw/return null等坏情况的分支
    public String methodSig;
    public HashSet<SrcMessage> values = new HashSet<>();
    public HashSet<Integer> paramInds = new HashSet<>();
    public List<String> defSources = new LinkedList<>();
    @JsonIgnore
    public CT relatedCT = null;

    public String getCTInfo() {
        if(relatedCT != null)
            return relatedCT.toString();
        return null;
    }

    public HashMap<String, String> constants = new HashMap<>();
    public Comparison comparison;
    @JsonIgnore
    public TransformableNode conditionNode;
    public boolean basic = false;
    @JsonIgnore
    public IfStmt ifStmt;
    @JsonIgnore
    public TransformableNode tfNode;
    public Pair<String, Integer> usedSite; // 被使用的位置信息记录
    public boolean satisfyFlag;
    boolean reverse = true;
    boolean deleteLast = false;

    public int type = SINGLE; // 标记属于 Single[0] / Or[1] / 环境变量[2]
    public static int SINGLE = 0; // = and
    public static int OR = 1;
    public static int UNCONTROLLABLE =2;

    public ConditionNode(){}

    public ConditionNode(TransformableNode tfNode, MethodDescriptor descriptor,
                         boolean satisfyFlag, boolean isBasic){
        this.conditionNode = tfNode;
        this.satisfyFlag = satisfyFlag;
        this.basic = isBasic;
        this.ifStmt = ((IfStmt) tfNode.node.unit);
        this.tfNode = tfNode;
        this.methodSig = descriptor.sootMethod.getSignature();
        this.usedSite = new Pair<>(tfNode.context.sootClass.getName(), InstrumentUtil.getLineNumberByUnit(tfNode.node.unit));

        Value conditionValue = ((IfStmt) tfNode.node.unit).getCondition();
        parseComparison(conditionValue);
        parseConditionValue(this.conditionNode, conditionValue, descriptor);
    }

    public ConditionNode(TransformableNode tfNode,
                         MethodDescriptor descriptor,
                         boolean isBasic){
        this.conditionNode = tfNode;
        this.satisfyFlag = true;
        this.basic = isBasic;
        this.ifStmt = ((IfStmt) tfNode.node.unit);
        this.tfNode = tfNode;
        this.methodSig = descriptor.sootMethod.getSignature();
        this.usedSite = new Pair<>(tfNode.context.sootClass.getName(), InstrumentUtil.getLineNumberByUnit(tfNode.node.unit));

        Value conditionValue = ((IfStmt) tfNode.node.unit).getCondition();
        parseComparison(conditionValue);
        parseConditionValue(this.conditionNode,conditionValue, descriptor);
    }

    public ConditionNode(TransformableNode tfNode,
                         boolean isBasic){
        this.conditionNode = tfNode;
        this.basic = isBasic;
//        this.ifStmt = ((IfStmt) tfNode.node.unit);
        this.tfNode = tfNode;
        this.methodSig = tfNode.method.getSignature();
        this.usedSite = new Pair<>(tfNode.context.sootClass.getName(), InstrumentUtil.getLineNumberByUnit(tfNode.node.unit));
    }

    public void parseComparison(Value condition){
        if (condition.toString().contains("==")){
            if (satisfyFlag)
                comparison = Comparison.EQUAL;
            else comparison = Comparison.NO_EQUAL_TO;
        }

        else if (condition.toString().contains("!=")){
            if (satisfyFlag)   comparison = Comparison.NO_EQUAL_TO;
            else comparison = Comparison.EQUAL;
        }

        else if (condition.toString().contains("<=")){
            if (satisfyFlag)   comparison = Comparison.SMALLER_OR_EQUAL;
            else comparison = Comparison.BIGGER;
        }

        else if (condition.toString().contains("<")){
            if (satisfyFlag)   comparison = Comparison.SMALLER;
            else comparison = Comparison.BIGGER_OR_EQUAL;
        }

        else if (condition.toString().contains(">=")){
            if (satisfyFlag)   comparison = Comparison.BIGGER_OR_EQUAL;
            else comparison = Comparison.SMALLER;
        }

        else if (condition.toString().contains(">")){
            if (satisfyFlag)   comparison = Comparison.BIGGER;
            else comparison = Comparison.SMALLER_OR_EQUAL;
        }
    }


    public void flipComparison(){
        switch (comparison){
            case SMALLER_OR_EQUAL:
                comparison = Comparison.BIGGER;
                break;
            case BIGGER_OR_EQUAL:
                comparison = Comparison.SMALLER;
                break;
            case SMALLER:
                comparison = Comparison.BIGGER_OR_EQUAL;
                break;
            case BIGGER:
                comparison = Comparison.SMALLER_OR_EQUAL;
                break;
            case EQUAL:
                comparison = Comparison.NO_EQUAL_TO;
                break;
            case NO_EQUAL_TO:
                comparison = Comparison.EQUAL;
        }
    }

    /**
     * 检测comparison与该condition node 的comparison是否矛盾
     * 不做过于惊喜的判别, 认为 > 和 >= 并不矛盾
     * @param comparison
     * @return
     */
    public boolean isNotContradictory(Comparison comparison){
        if (this.comparison.equals(Comparison.EQUAL))
            return comparison.equals(Comparison.EQUAL);
        if (this.comparison.equals(Comparison.NO_EQUAL_TO))
            return !comparison.equals(Comparison.EQUAL);
        if (this.comparison.equals(Comparison.BIGGER) | this.comparison.equals(Comparison.BIGGER_OR_EQUAL))
            return comparison.equals(Comparison.BIGGER) | comparison.equals(Comparison.BIGGER_OR_EQUAL);
        if (this.comparison.equals(Comparison.SMALLER) | this.comparison.equals(Comparison.SMALLER_OR_EQUAL))
            return comparison.equals(Comparison.SMALLER) | comparison.equals(Comparison.SMALLER_OR_EQUAL);
        return false;
    }

    public void parseConditionValue(TransformableNode tfNode,
                                    Value condition,
                                    MethodDescriptor descriptor){
        for (ValueBox valueBox: condition.getUseBoxes()){
            // 如果是常量，则认为是条件限制变量
            if (valueBox.getValue() instanceof Constant){ // Constant
                if (reverse & !comparison.equals(Comparison.EQUAL) & !comparison.equals(Comparison.NO_EQUAL_TO))
                    flipComparison();
                constants.put(valueBox.getValue().getType().toString(), valueBox.getValue().toString());
            }else {
                // 先直接去存储下的field/tainted input污染Map中查找，检测是否是与某一个field/tainted input params相关
                if (!ConditionUtils.findControllableSourceDirect(this, descriptor,valueBox.getValue()) || true) { // 永远去查找
                    // 可能存在一些复杂一些的数据流，查找所有定义语句并进行进一步解析
                    try {
                        HashSet<Node> sources = findAllDefUnitAffectThisValue(tfNode.node, valueBox);
                        ConditionUtils.parseSources(this, sources, valueBox.getValue(), descriptor);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
            reverse = false;
        }

        if (deleteLast)
            constants.remove(constants.size()-1);
    }

    public Integer getHashCode() {
        if (ifStmt != null)
            return ifStmt.hashCode();
        return tfNode.node.unit.hashCode();
    }

    public int getComplexity() {
        // todo: 暂时都指定为1
        if(values.contains(null)) {
            // == null类型
            return 1;
        }
        return 1;
    }
}
