package edu.fudan.pterosaur.script;
import edu.fudan.pterosaur.basic.common.SootInit;
import soot.*;
import soot.options.Options;

import java.util.*;

import soot.jimple.internal.JIfStmt;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

public class BranchExtractor {
    public static void main(String[] args) {
        SootInit.setSoot_inputClass("/Users/luca/dev/2024/pangaea/rosefinch/target/classes");
        // 加载目标类
        String targetClass = "edu.fudan.rosefinch.factory.MyObjectFactory"; // 替换为你的类名
        SootClass sootClass = Scene.v().loadClassAndSupport(targetClass);
        sootClass.setApplicationClass();

        SootMethod targetMethod = sootClass.getMethodByName("getObjectIf");
        Body body = targetMethod.retrieveActiveBody();

        // 构建控制流图
        UnitGraph cfg = new BriefUnitGraph(body);

        // 存储每个分支的条件及其对应的 Units
        Map<String, List<Unit>> branchMap = new LinkedHashMap<>();

        // 遍历控制流图中的每个 Unit
        for (Unit unit : cfg) {
            // 检查是否是 IfStmt
            if (unit instanceof JIfStmt) {
                JIfStmt ifStmt = (JIfStmt) unit;

                // 获取条件表达式
                String condition = ifStmt.getCondition().toString();

                // 使用 DFS 获取条件分支内的所有 Unit
                List<Unit> branchUnits = getBranchUnits(cfg, ifStmt.getTarget(), unit);

                // 将结果存储到 Map 中
                branchMap.put(condition, branchUnits);
            }
        }

        // 输出每个分支的条件和对应的 Units
        for (Map.Entry<String, List<Unit>> entry : branchMap.entrySet()) {
            System.out.println("Condition: " + entry.getKey());
            System.out.println("Units:");
            for (Unit unit : entry.getValue()) {
                System.out.println("  " + unit);
            }
        }
    }

    // 获取指定分支的所有 Unit
    private static List<Unit> getBranchUnits(UnitGraph cfg, Unit target, Unit branchStart) {
        List<Unit> branchUnits = new ArrayList<>();
        Set<Unit> visited = new HashSet<>();
        Deque<Unit> stack = new ArrayDeque<>();

        stack.push(target);
        while (!stack.isEmpty()) {
            Unit current = stack.pop();

            // 防止循环遍历
            if (visited.contains(current)) continue;
            visited.add(current);

            // 添加到分支列表
            branchUnits.add(current);

            // 检查是否到达合并点或跳转语句
            if (current.equals(branchStart)) break;

            // 添加后继节点
            stack.addAll(cfg.getSuccsOf(current));
        }

        return branchUnits;
    }
}