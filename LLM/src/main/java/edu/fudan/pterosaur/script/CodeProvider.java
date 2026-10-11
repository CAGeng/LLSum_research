package edu.fudan.pterosaur.script;

import edu.fudan.pterosaur.basic.cg.CG;
import edu.fudan.pterosaur.basic.common.SootInit;
import edu.fudan.pterosaur.basic.util.SignatureProcessor;
import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;

import java.io.*;
import java.util.*;

/**
 * User: luca
 * Date: 2024/11/26
 * Description:
 */
public class CodeProvider {

    public static String filePath = "/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/output/tpl_sort.txt";

    public static String[] classPath = new String[]{"/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream/amqp-client-5.22.0.jar",
            "/Users/luca/dev/2025/pterosaur/output/popular-components/amqp-client/downstream/vertx-rabbitmq-client-4.5.10.jar"};
    public static String outputPath = "/Users/luca/dev/2025/pterosaur/llm/input/code/pilot-8.txt";
    public static boolean checkOne = false;

    public static int dep = 3;

    public static void main(String[] args) {

        if (args.length == 5){
            filePath = args[0];
            classPath = args[1].split(",");
            outputPath = args[2];
            checkOne = Boolean.parseBoolean(args[3]);
            dep = Integer.parseInt(args[4]);
            // 打印所有参数
            System.out.println("Arguments: " + Arrays.toString(args));
        }

        SootInit.setSoot_inputClass(List.of(classPath), true);

        // 读取签名并处理
        Map<String, List<String>> map = SignatureProcessor.processSignatures(filePath);


        analysisOneByOne(map);
    }

    private static void analysisOneByOne(Map<String, List<String>> map) {

        System.out.println("****** AnalysisOneByOne ******");

        // 遍历 map 按照插入顺序
        for (Map.Entry<String, List<String>> entry : map.entrySet()) {

            String key = entry.getKey();
            List<String> valueList = entry.getValue();
            // 打印 key
            System.out.println("API in TPL : " + key);


            SootMethod calleeMethod;
            try {
                calleeMethod = Scene.v().getMethod(key);
            }catch (Exception e){
                System.out.println("calleeMethod not found : " + key);
                e.printStackTrace();
                continue;
            }

            // 遍历 list，打印其中的每一个 value
            List<SootMethod> entries = new ArrayList<>();
            for (String value : valueList) {
                try{
                    SootMethod targetMethod = Scene.v().getMethod(value);
                    if (targetMethod != null) {
                        entries.add(targetMethod);
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            if (!checkOne){
                System.out.println("check all entries : " + entries.size());
                CG cg = new CG(entries);
                System.out.println("cg size : " + cg.callGraph.size());
            }

            for (SootMethod sootMethod : entries) {
                if (checkOne){
                    System.out.println("only check one entry");
                    CG cg = new CG(Collections.singletonList(sootMethod));
                    System.out.println("cg size : " + cg.callGraph.size());
                }
                extracted(sootMethod, calleeMethod);
            }
        }
    }

    private static void extracted(SootMethod targetMethod, SootMethod calleeMethod) {
        try{
            CallGraph cg = Scene.v().getCallGraph();
            Iterator<Edge> edges = cg.edgesOutOf(targetMethod);

            SootMethod cMethod = null;

            while (edges.hasNext()) {
                SootMethod callee = edges.next().getTgt().method();
                System.out.println("callee in first dep" + callee);
                if (calleeMethod.getSignature().equals(callee.getSignature())){
                    cMethod = calleeMethod;
                    break;
                }else if (isImplementation(calleeMethod, callee)){
                    cMethod = callee;
                    break;
                }

            }

            if (cMethod != null){
                // 生成调用图
                //CHATransformer.v().transform();
                // 存储方法体的结果
                List<SootMethod> methodCallChains = new LinkedList<>();
                methodCallChains.add(cMethod);
                analyzeMethod(cMethod, dep, methodCallChains);

                analyzeAndWriteToFile(methodCallChains, outputPath, targetMethod, cMethod);
            }
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    /**
     * 分析指定方法的调用链，并存储结果
     *
     * @param method           当前方法
     * @param depth            剩余递归深度
     * @param methodCallChains 调用链数据结构
     */
    private static void analyzeMethod(SootMethod method, int depth, List<SootMethod> methodCallChains) {
        if (depth <= 0 || method == null || !method.hasActiveBody()) return;
        CallGraph cg = Scene.v().getCallGraph();
        Iterator<Edge> edges = cg.edgesOutOf(method);

        while (edges.hasNext()) {
            SootMethod callee = edges.next().getTgt().method();
            if (!callee.getSignature().startsWith("<java.") && !callee.getSignature().startsWith("<javax.")){
                methodCallChains.add(callee);
                analyzeMethod(callee, depth - 1, methodCallChains);
            }
        }
    }

    // 判断 callee 是否是 calleeMethod 的具体实现
    private static boolean isImplementation(SootMethod calleeMethod, SootMethod callee) {
        // 检查方法名和参数
        if (callee.getName().equals(calleeMethod.getName()) &&
                callee.getParameterTypes().equals(calleeMethod.getParameterTypes())) {

            // 方法名和参数匹配，进一步检查父类和接口
            SootClass calleeClass = callee.getDeclaringClass();
            SootClass calleeMethodClass = calleeMethod.getDeclaringClass();

            // 检查实现的接口
            if (calleeClass.getInterfaces().contains(calleeMethodClass)) {
                return true; // callee 是 calleeMethod 的接口实现
            } else {
                // 检查父类
                while (calleeClass != null) {
                    if (calleeClass.equals(calleeMethodClass)) {
                        return true; // callee 是 calleeMethod 的具体实现
                    }
                    calleeClass = calleeClass.getSuperclass();
                }
            }
        }
        return false; // callee 不是 calleeMethod 的实现
    }


    private static Set<String> processSignatures(String inputFile){
        // 读取文件并处理签名
        Set<String> uniqueSignatures = new LinkedHashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // 提取 < 开始到 <--- 为止的部分作为签名
                int startIdx = line.indexOf('<');
                int endIdx = line.indexOf("<---");

                if (startIdx != -1 && endIdx != -1) {
                    String signature = line.substring(startIdx, endIdx).trim();

                    // 处理签名中的 -InterfaceClass 和 -SuperClass
                    signature = signature.replaceAll("-InterfaceClass|-SuperClass", "");

                    // 添加到 Set 中去重
                    uniqueSignatures.add(signature);
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return uniqueSignatures;
    }

    private static void writeSignaturesToFile(Set<String> uniqueSignatures, String outputFile) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            for (String signature : uniqueSignatures) {
                writer.write(signature);
                writer.newLine();
            }
        }
    }


    private static void excludeJDKLibrary()
    {
        //exclude jdk classes
        Options.v().set_exclude(excludeList());
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
    }

    private static List<String> excludeList() {
        LinkedList<String> excludeList = new LinkedList<String> (); // 扩展的基本函数package
        excludeList.add("java.");
        excludeList.add("javax.");
        return excludeList;
    }

    private CallGraph constructCG(List<SootMethod> entryPoints){
        releaseCallgraph();
        excludeJDKLibrary();
        Scene.v().setEntryPoints(entryPoints);
        PackManager.v().runPacks();
        return Scene.v().getCallGraph();
    }

    protected void releaseCallgraph() {
        Scene.v().releaseCallGraph();
        Scene.v().releasePointsToAnalysis();
        Scene.v().releaseReachableMethods();
        G.v().resetSpark();
    }

    public static void analyzeAndWriteToFile(List<SootMethod> callees, String outputFilePath, SootMethod targetMethod, SootMethod cMethod) throws IOException {
        // 创建 BufferedWriter 来写文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFilePath, true))) {
            // 1. 写入第一行：Method to be analyzed: $methodName
            if (callees.isEmpty()) {
                return;
            }
            SootMethod firstMethod = callees.get(0);
            writer.write("Method to be analyzed: " + firstMethod.getSignature());
            writer.newLine();
            writer.write("Related methods: " + callees.size());
            writer.newLine();
            writer.write("caller is : " + targetMethod.getSignature());
            writer.newLine();
            writer.write("callee is : " + cMethod.getSignature());
            writer.newLine();
            writer.newLine();

            // 2. 分析每个方法并写入文件
            for (SootMethod callee : callees) {
                // 获取方法签名和方法体（body）
                String methodSignature = callee.getSignature();
                String methodBody = getMethodBody(callee);

                // 3. 写入方法签名
                writer.write("Method: " + methodSignature);
                writer.newLine();

                // 4. 写入方法体
                writer.write(methodBody);
                writer.newLine();
            }

            // 加个分隔符，方便区分不同方法
            writer.write("-----------");
            writer.newLine();
        }
    }

    // 解析方法体（获取具体的 body）
    public static String getMethodBody(SootMethod method) {
        // 获取方法的 body，若为抽象方法则返回"Abstract method"
        if (method.hasActiveBody()) {
            Body body = method.getActiveBody();
            return body.toString();  // 返回方法体的字符串表示
        } else {
            return "Abstract method"; // 对于没有实现的接口方法或者抽象方法，返回提示
        }
    }

}
