package intelligent.datacollect;

import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.cg.CG;
import soot.SootMethod;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;


class MethodInfo {
    String name;
    String signature;
    int parameterCount;
    int callSiteCount;

    MethodInfo(String name, String signature, int parameterCount, int callSiteCount) {
        this.name = name;
        this.signature = signature;
        this.parameterCount = parameterCount;
        this.callSiteCount = callSiteCount;
    }
}

public class MethodData {

    public static void methodCallSiteInCG(CG cg) {
        List<MethodInfo> methodInfoList = new ArrayList<>();

        for (SootMethod sootMethod : cg.getAllReachableMethodFromEntry()) {
            String name = sootMethod.getName();
            String signature = sootMethod.getSignature();
            int parameterCount = sootMethod.getParameterCount();
            HashSet<SootMethod> callers = cg.edgesInto(sootMethod);
            int callSiteCount = callers.size();

            if (sootMethod.getSignature().startsWith("<java") || sootMethod.isJavaLibraryMethod())
                continue;

            if(sootMethod.getSignature().startsWith("<org.eclipse.jdt")) continue;

            MethodInfo methodInfo = new MethodInfo(name, signature, parameterCount, callSiteCount);
            methodInfoList.add(methodInfo);
        }

        // 按照call-site个数排序
        Collections.sort(methodInfoList, Comparator.comparingInt((MethodInfo m) -> m.callSiteCount).reversed());

        GeneralConfig generalConfig = SpringContextUtils.getBean(GeneralConfig.class);

        // 输出到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(generalConfig.sootOutputPath).resolve("method_info.txt").toString()))) {
            for (MethodInfo methodInfo : methodInfoList) {
                writer.write("方法名：" + methodInfo.name + "\n");
                writer.write("方法签名：" + methodInfo.signature + "\n");
                writer.write("方法的参数个数：" + methodInfo.parameterCount + "\n");
                writer.write("call-site个数： " + methodInfo.callSiteCount + "\n\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void collectLibCalls(CG cg) {

        GeneralConfig generalConfig = SpringContextUtils.getBean(GeneralConfig.class);
        // 根据输入目录检测基本的类名
        List<String> sootInputPaths = generalConfig.sootInputPaths;
        List<String> allPackagePaths = new LinkedList<>();
        for(String sootInputPath : sootInputPaths) {
            try {
                List<String> packagePaths = getClassPackagePaths(sootInputPath);
                allPackagePaths.addAll(packagePaths);
            } catch (IllegalArgumentException e) {
                System.err.println(e.getMessage());
            }
        }
        // 去重
        Set<String> uniquePackagePaths = new HashSet<>(allPackagePaths);

        // 输出结果
//        System.out.println("定位到的 app package 列表:");
//        for (String packagePath : uniquePackagePaths) {
//            System.out.println(packagePath);
//        }

        HashSet<SootMethod> calledLibMethods = new HashSet<>();

        for (SootMethod sootMethod : cg.getAllReachableMethodFromEntry()) {

            if (sootMethod.getSignature().startsWith("<java") || sootMethod.isJavaLibraryMethod())
                continue;

            if(sootMethod.getSignature().startsWith("<org.eclipse.jdt")) continue;
            if(sootMethod.getSignature().startsWith("<jdk.")) continue;

            boolean inApp = false;
            for(String packagePath : allPackagePaths) {
                if (sootMethod.getDeclaringClass().getName().contains(packagePath)) {
                    inApp = true;
                    break;
                }
            }
            if(inApp) continue;

            calledLibMethods.add(sootMethod);
        }

        // 输出到文件
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(Paths.get(generalConfig.sootOutputPath).resolve("calledLibMethods.txt").toString()))) {
            for (SootMethod sootMethod : calledLibMethods) {
                writer.write("方法：" + sootMethod + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫描指定目录，获取包含 .class 文件的所有目录，并转换为包名格式
     * @param rootDir 需要扫描的根目录
     * @return 包含 .class 文件的目录列表，格式为 a.b.c
     */
    public static List<String> getClassPackagePaths(String rootDir) {
        List<String> packageList = new ArrayList<>();
        File root = new File(rootDir);

        if (!root.exists() || !root.isDirectory()) {
            throw new IllegalArgumentException("指定的路径不是有效的目录: " + rootDir);
        }

        // 递归扫描目录
        scanDirectory(root, root, packageList);

        return packageList;
    }

    private static void scanDirectory(File root, File currentDir, List<String> packageList) {
        // 获取当前目录下的文件和子目录
        File[] files = currentDir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        boolean containsClassFile = false;

        // 检查当前目录是否包含 .class 文件
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".class")) {
                containsClassFile = true;
                break;
            }
        }

        // 如果包含 .class 文件，计算相对路径并转换为包名格式
        if (containsClassFile) {
            if(currentDir.getAbsolutePath().equals(root.getAbsolutePath())) {
                packageList.add("[root(no relative-path)]");
            } else {
                String relativePath = currentDir.getAbsolutePath().substring(root.getAbsolutePath().length() + 1);
                String packageName = relativePath.replace(File.separator, ".");
                packageList.add(packageName);
            }
        }

        // 递归处理子目录
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(root, file, packageList);
            }
        }
    }

}
