package edu.fudan.pterosaur.script;

import edu.fudan.pterosaur.basic.common.SootInit;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JIfStmt;
import soot.toolkits.graph.*;

import java.util.*;

public class EnumTypeBindingAnalysis {
    public static void main(String[] args) {
        SootInit.setSoot_inputClass("/Users/luca/dev/2024/pangaea/rosefinch/target/classes");
        // 加载目标类
        String targetClass = "edu.fudan.rosefinch.factory.MyObjectFactory"; // 替换为你的类名
        SootClass sootClass = Scene.v().loadClassAndSupport(targetClass);
        sootClass.setApplicationClass();

        SootMethod targetMethod = sootClass.getMethodByName("getObjectIf");
        Body body = targetMethod.retrieveActiveBody();

        // 构建基本块图
        BlockGraph blockGraph = new BriefBlockGraph(body);

        // 遍历每个基本块
        for (Block block : blockGraph) {
            System.out.println("Block: " + block.getIndexInMethod());
            System.out.println("  Successors: " + block.getSuccs());
            System.out.println("  Units:");
            for (Unit unit : block) {
                System.out.println("    " + unit);
            }
        }
    }


    private static String analyzeCaseBranch(Body body, Unit caseUnit) {
        // 遍历 case 分支中的指令
        Iterator<Unit> iterator = body.getUnits().iterator(caseUnit);
        while (iterator.hasNext()) {
            Unit unit = iterator.next();

            // 检查是否有 new 操作
            if (unit.toString().contains("new ")) {
                return unit.toString().split("new ")[1].split(" ")[0];
            }

            // 遇到 return 结束分析
            if (unit instanceof soot.jimple.ReturnStmt) {
                break;
            }
        }
        return null;
    }
}
