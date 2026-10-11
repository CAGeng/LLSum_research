package edu.fudan.pterosaur.basic.common;

import java.util.Arrays;
import java.util.List;

public class Config {
    public static String rtDir = "";
    public static String jceDir = "";
    public static String dependency_path = "";

    public static List<String> xmlDirs = Arrays.asList(
            "/Users/luca/dev/research/micro_service_seclab-main/src/main/resources/mappers"
    );

    public static List<String> sootInputPaths = Arrays.asList(
            "/Users/luca/dev/research/spel/target/classes"
            ,"/Users/luca/tmp/jars/BOOT-INF/lib/spring-expression-5.3.16.jar"
            ,"/Users/luca/tmp/b/BOOT-INF/lib/httpclient-4.5.13.jar"
            ,"/Users/luca/tmp/b/BOOT-INF/lib/fluent-hc-4.5.13.jar"
            ,"/Users/luca/tmp/b/BOOT-INF/lib/okhttp-2.7.5.jar"
    );
    public static String outputPath = "/Users/luca/dev/research/output";
    public static String additionPath = "/Users/fortsun/Downloads/题目2：源码自动化审计与自动化评估验证/赛题材料/micro_service_seclab-main/out/artifacts/micro_service_seclab_jar/micro-service-seclab.jar";
    public static String  outputFileName = "Analyze_result.json";

}


