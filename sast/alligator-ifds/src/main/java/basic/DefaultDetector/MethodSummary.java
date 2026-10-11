package basic.DefaultDetector;

import com.fasterxml.jackson.annotation.JsonIgnore;
import intelligent.ConditionParser;
import soot.SootMethod;

import java.util.*;

public class MethodSummary {
    public String methodSig = "";
    private HashMap<String, String> conditionalTaint = new HashMap<>();
    public HashSet<String> tags = new HashSet<>();

    public HashMap<String, String> detailedTransferMap = new HashMap<>();
    public int reuse_con = 0;

    @JsonIgnore
    public String sliceCode = "";
    @JsonIgnore
    public String code = "";

    @JsonIgnore
    public String dataflowExplanation = "";

    //////////////////////////////////// sft-2-25 condition record //////////////////////////////////////
    public HashMap<String, ConditionParser.ParseResult> transferMapCondition = new HashMap<>();

    public HashMap<String, String> getConditionalTaint() {
        return conditionalTaint;
    }

    // Conditional Taint
    public MethodSummary(){}
    public MethodSummary(SootMethod method) {
        methodSig = method.getSignature();
    }

    // call-site taints 和 return-site taints 之间的关系
    public void addConditionalTaint(List<Integer> csTaints, List<Integer> rsTaints) {
        Collections.sort(csTaints);
        Collections.sort(rsTaints);
        if(!csTaints.toString().equals(rsTaints.toString())) tags.add("CHANGE");
        if(!rsTaints.isEmpty()) tags.add("NotEmpty");
        conditionalTaint.put(csTaints.toString(), rsTaints.toString());
    }

    public List<Integer> getConditionalTaint(List<Integer> csTaints) {
        Collections.sort(csTaints);
        String rsTaints = conditionalTaint.get(csTaints.toString());

        // 将 rsTaints 字符串转换为 List<Integer>
        List<Integer> resultList = new LinkedList<>();
        if(rsTaints == null) return null;
        if (rsTaints != null && !rsTaints.isEmpty()) {
            rsTaints = rsTaints.substring(1, rsTaints.length() - 1);
            String[] parts = rsTaints.split(",");
            for (String part : parts) {
                try {
//                    System.out.println(part);
                    resultList.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException e) {
                    // 处理可能的数字格式异常
                }
            }
        }
        return resultList;
    }

    public static void main(String[] args) {
        MethodSummary example = new MethodSummary();

        // 创建两个列表用于测试
        List<Integer> csTaints = Arrays.asList(3, 1, 4, 1, 5);
        List<Integer> rsTaints = Arrays.asList(2, 7, 1, 8, 2);

        // 添加条件污点映射
        example.addConditionalTaint(csTaints, rsTaints);
        System.out.println(example.conditionalTaint);

        // 检索条件污点
        List<Integer> retrievedTaints = example.getConditionalTaint(csTaints);

        // 打印结果
        System.out.println("Retrieved Taints: " + retrievedTaints);
    }

}
