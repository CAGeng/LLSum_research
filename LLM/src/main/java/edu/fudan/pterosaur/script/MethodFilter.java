package edu.fudan.pterosaur.script;

import edu.fudan.pterosaur.basic.common.SootInit;
import soot.Modifier;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.List;

public class MethodFilter {

    public static String[] classPaths = new String[]{
            "/Users/luca/dev/2025/pterosaur/output/popular-components/aliyun-java-sdk-core/downstream/aliyun-java-sdk-core-4.7.2.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream/amqp-client-5.22.0.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/aws-java-sdk-core/downstream/aws-java-sdk-core-1.12.777.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/fastjson/downstream/fastjson-2.0.53.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/guava/downstream/guava-33.3.1-jre.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/hutool-core/downstream/hutool-core-5.8.32.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/kubernetes-client/downstream/kubernetes-client-6.13.4.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/joda-time/downstream/joda-time-2.13.0.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/minio/downstream/minio-8.5.13.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/weixin-java-miniapp/downstream/weixin-java-miniapp-4.6.7.B.jar"
    };

    public static void main(String[] args) {
        for (String classPath : classPaths) {
            // 设置 Soot 输入类路径
            SootInit.setSoot_inputClass(List.of(classPath), true);

            // 存储 public 和 protected 方法的数量
            int publicMethodCount = 0;
            int protectedMethodCount = 0;

            // 遍历所有类
            for (SootClass sootClass : Scene.v().getApplicationClasses()) {
                // 遍历类中的所有方法
                for (SootMethod method : sootClass.getMethods()) {
                    // 检查修饰符
                    if (Modifier.isPublic(method.getModifiers())) {
                        publicMethodCount++;
                    } else if (Modifier.isProtected(method.getModifiers())) {
                        protectedMethodCount++;
                    }
                }
            }

            // 输出结果
            System.out.println("JAR: " + classPath);
            System.out.println("Public methods count: " + publicMethodCount);
            System.out.println("Protected methods count: " + protectedMethodCount);
            System.out.println("-----------------------------");
        }
    }
}