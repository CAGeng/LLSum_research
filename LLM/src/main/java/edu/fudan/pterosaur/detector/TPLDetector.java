package edu.fudan.pterosaur.detector;

import edu.fudan.pterosaur.configuration.GeneralConfig;
import edu.fudan.pterosaur.configuration.GeneralList;
import edu.fudan.pterosaur.util.LocalStorage;
import javassist.*;
import javassist.Modifier;
import javassist.bytecode.Bytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.LineNumberAttribute;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;

import javax.annotation.PostConstruct;
import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * User: luca
 * Date: 2024/10/17
 * Description:
 */


@Component
public class TPLDetector {

    @Autowired
    GeneralList generalList;

    @Autowired
    GeneralConfig generalConfig;

    private static Set<String> calleeRecord = new HashSet<>();

    @PostConstruct
    void init() {
        // 创建ClassPool并将JAR文件添加到ClassPool
        ClassPool pool = ClassPool.getDefault();
        try {
            for (String path : generalList.sootInputPaths) {
                File file = new File(path);
                if (file.isFile() && path.endsWith(".jar")) {
                    // 如果是 JAR 文件，直接添加到 ClassPool
                    pool.appendClassPath(file.getAbsolutePath());
                } else if (file.isDirectory()) {
                    // 如果是文件夹，递归处理其中的 JAR 文件
                    loadJarsFromDirectory(path, pool);
                }
            }
        } catch (NotFoundException e) {
            throw new RuntimeException("Failed to add JAR paths to ClassPool", e);
        }
    }

    // 递归加载文件夹中的所有 JAR 文件
    private void loadJarsFromDirectory(String directoryPath, ClassPool pool) throws NotFoundException {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) {
            return; // 如果不是文件夹，直接返回
        }

        // 遍历文件夹中的每个文件或子文件夹
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                // 如果是子文件夹，递归处理
                loadJarsFromDirectory(file.getAbsolutePath(), pool);
            } else if (file.isFile() && file.getName().endsWith(".jar")) {
                // 如果是 JAR 文件，将其添加到 ClassPool
                pool.appendClassPath(file.getAbsolutePath());
            }
        }
    }


    public void detect() {
        Collection<SootClass> classSnapshot = new ArrayList<>(Scene.v().getApplicationClasses());
        classSnapshot.removeIf(sootClass ->
                generalList.targetPackages.stream().anyMatch(targetPackage ->
                        sootClass.getPackageName().startsWith(targetPackage) && !sootClass.getName().endsWith("Test")
                )
        );
        System.out.println("classSnapshot size is : " + classSnapshot.size());
        List<String> list = new CopyOnWriteArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);
        for (SootClass sootClass : classSnapshot) {
            detectThirdPartyCalls(sootClass, list);
            // 每处理完一个 SootClass，计数器加1
            int count = counter.incrementAndGet();
            //System.out.println("Processed SootClass count: " + count+ " : " + sootClass.getName());
        }

        try (FileWriter writer = new FileWriter("output/tpl.txt")) {
            for (String str : list) {
                writer.write(str + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void detectThirdPartyCalls(SootClass appClass, List list) {

        boolean needWrite = false;

        try {
            Iterator<SootMethod> methodIterator = appClass.getMethods().iterator();
            while (methodIterator.hasNext()) {
                SootMethod method = methodIterator.next();
                Body body;
                try {
                    body = method.retrieveActiveBody();
                } catch (Exception e) {
                    continue;
                }
                for (Unit unit : body.getUnits()) {
                    Stmt stmt = (Stmt) unit;
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr invokeExpr = stmt.getInvokeExpr();
                        SootMethod calledMethod = invokeExpr.getMethod();

                        // 获取方法所属类的包名
                        String packageName = calledMethod.getDeclaringClass().getPackageName();
                        if (isThirdPartyPackage(packageName) && isTargetPackage(packageName)) {

                            // 遍历调用方法的参数
                            StringBuilder calledMethodSignature = new StringBuilder(calledMethod.getSignature());
                            List<Type> parameterTypes = calledMethod.getParameterTypes();
                            for (int i = 0; i < parameterTypes.size(); i++) {
                                Type paramType = parameterTypes.get(i);
                                if (paramType instanceof RefType) {
                                    SootClass paramClass = ((RefType) paramType).getSootClass();

                                    // 创建一个标签变量，用于累积所有满足条件的标签
                                    StringBuilder label = new StringBuilder(paramClass.getName());

                                    if (!paramClass.getName().startsWith("java.")) { // 忽略 java 标准库类型
                                        // 判断该参数类型是否是枚举
                                        if (paramClass.isEnum() && !label.toString().contains("-Enum")) {
                                            label.append("-Enum");
                                        }
                                        // 判断该参数类型是否是接口
                                        if (paramClass.isInterface() && !label.toString().contains("-InterfaceClass")) {
                                            label.append("-InterfaceClass");
                                        }
                                        // 判断该参数类型是否是超类
                                        if (paramClass.hasSuperclass() && !label.toString().contains("-SuperClass")) {
                                            label.append("-SuperClass");
                                        }
                                    }

                                    // 在输出中替换原始参数类型为带有标签的类型
                                    calledMethodSignature = new StringBuilder(calledMethodSignature.toString()
                                            .replace(paramClass.getName(), label.toString()));
                                    System.out.println("Method: " + method.getSignature() +
                                            " calls third-party method with tagged parameter: " + calledMethodSignature);
                                }
                            }

                            // 输出修改后的信息
                            System.out.println("Method: " + method.getSignature() +
                                    " calls third-party method: " + calledMethodSignature);
                            list.add(method.getSignature() + "--->" + calledMethodSignature);


                            // 在此插入插桩代码
                            if (generalConfig.onlyOneCallee){
                                if (!calleeRecord.contains(calledMethodSignature.toString())){
                                    calleeRecord.add(calledMethodSignature.toString());
                                    boolean judge = instrumentMethod(appClass, method, calledMethod);
                                    if (judge){
                                        needWrite = true;
                                    }
                                }
                            }else{
                                boolean judge = instrumentMethod(appClass, method, calledMethod);
                                if (judge){
                                    needWrite = true;
                                }
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (needWrite){
            // After instrumenting all methods, save the class to a new JAR
            saveToNewJar(appClass, generalConfig.sinkClassPath);
        }
    }

    private boolean isThirdPartyPackage(String packageName) {
        for (String pn : generalList.tplPackages) {
            if (packageName.startsWith(pn)) {
                return true;
            }
        }

        for (String pn : generalList.appPackages) {
            if (!packageName.startsWith(pn) && !packageName.startsWith("java.")&& !packageName.startsWith("javax.") && !packageName.startsWith("soot.") && !packageName.startsWith("org.springframework") && !packageName.startsWith("io.swagger")
                    && !packageName.startsWith("org.slf4j") && !packageName.startsWith("com.github.pagehelper")) {
                return true;
            }
        }
        return false;
    }

    private boolean isTargetPackage(String packageName) {
        if (generalList.targetPackages == null || generalList.targetPackages.size() == 0) {
            return true;
        }
        for (String pn : generalList.targetPackages) {
            if (packageName.startsWith(pn)) {
                return true;
            }
        }
        return false;
    }



    private void saveToNewJar(SootClass appClass, String outputJarPath) {
        try {
            // First, check if the JAR file exists
            File jarFile = new File(outputJarPath);
            boolean isNewJar = !jarFile.exists();

            // Prepare the output JAR file stream
            FileOutputStream fos = new FileOutputStream(outputJarPath, true);  // true to append
            JarOutputStream jarOut;

            // If the JAR file exists, we need to append it
            if (!isNewJar) {
                jarOut = new JarOutputStream(fos);
                // Copy existing entries to the new JAR to avoid losing them
                try (JarFile existingJarFile = new JarFile(jarFile)) {
                    addAllEntriesWithoutClass(existingJarFile, jarOut, appClass.getName().replace('.', '/') + ".class");
                }
            } else {
                // If it's a new JAR, we can create a new JAR file stream
                jarOut = new JarOutputStream(fos, new Manifest());
            }

            // Prepare the bytecode of the modified class using Javassist
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(appClass.getName());

            // Get bytecode of the modified class
            byte[] bytecode = ctClass.toBytecode();

            // We will save the modified class files
            String classFilePath = appClass.getName().replace('.', '/') + ".class";

            // Create the new entry for the modified class
            jarOut.putNextEntry(new JarEntry(classFilePath));
            jarOut.write(bytecode);
            jarOut.closeEntry(); // Close the entry


            // Save inner classes
            CtClass[] innerClasses = ctClass.getNestedClasses();
            for (CtClass innerClass : innerClasses) {
                String innerClassFilePath = innerClass.getName().replace('.', '/') + ".class";
                byte[] innerBytecode = innerClass.toBytecode();
                try {
                    jarOut.putNextEntry(new JarEntry(innerClassFilePath));
                    jarOut.write(innerBytecode);
                    jarOut.closeEntry();
                }catch (Exception e){
                    System.out.println("add inner class failed : " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Close the JAR output stream
            jarOut.close();
            fos.close();

            System.out.println("JAR file saved to " + outputJarPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addAllEntriesWithoutClass(JarFile jarFile, JarOutputStream jarOut, String excludedClass) throws IOException {
        // Copy all entries from the existing jar except the excluded class
        jarFile.stream().forEach(entry -> {
            try {
                if (!entry.getName().equals(excludedClass)) {
                    jarOut.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream input = jarFile.getInputStream(entry)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = input.read(buffer)) != -1) {
                            jarOut.write(buffer, 0, length);
                        }
                    }
                    jarOut.closeEntry();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private boolean instrumentMethod(SootClass appClass, SootMethod method, SootMethod calledMethod) {
        System.out.println("Starting Instrument : " + method.getSignature() + " --> " + calledMethod.getSignature());
        try {
            // 使用Javassist插桩
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(appClass.getName());
            CtMethod ctMethod = ctClass.getDeclaredMethod(method.getName());

            // 获取方法调用的返回值类型
            Type returnType = calledMethod.getReturnType();

            // 在调用calledMethod的下一句插入获取返回值的代码
            ctMethod.instrument(new ExprEditor() {
                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals(calledMethod.getName())) {

                        boolean flag = false;
                        try{
                            if (!Modifier.isStatic(m.getMethod().getModifiers())){
                                flag = true;
                            }
                        }catch (Exception e){
                            System.out.println("check static failed : " + m);
                            e.printStackTrace();
                            return;
                        }

                        StringBuilder str = new StringBuilder();


                        if (!LocalStorage.map.containsKey(appClass.getName())){
                            LocalStorage.map.put(appClass.getName(), new AtomicInteger(1));
                        }


                        try {
                            if (!(returnType instanceof VoidType)) {
                                str.append("{ $_ = $proceed($$); ");
                                generateSinkCalls(str, "$_", returnType, 3, LocalStorage.map.get(appClass.getName()));
                            } else {
                                str.append("{ $proceed($$); ");
                            }
                            if (flag) {
                                generateSinkCalls(str, "$0", returnType, 3, LocalStorage.map.get(appClass.getName()));
                            }
                            str.append("}");

                            System.out.println("flag : " + flag);
                            System.out.println("replace str : " + str);
                            m.replace(str.toString());
                        } catch (Exception e) {
                            System.out.println("instrument sink failed : " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            });

            // 如果返回值是引用类型
//            if (returnType instanceof RefType) {
//                String returnClassName = ((RefType) returnType).getClassName();
//                CtClass returnCtClass = pool.get(returnClassName);
//
//                // 如果返回值是String类型
//                if (returnClassName.equals("java.lang.String")) {
//                    ctMethod.insertAfter("com.example.demo.Instrument.sink($_);", true);
//                } else {
//                    // 返回值是复杂对象类型，遍历其字段并插入sink调用
//                    CtField[] fields = returnCtClass.getDeclaredFields();
//
//                    for (CtField field : fields) {
//                        ctMethod.insertAfter("com.example.demo.Instrument.sink($_." + field.getName() + ");", true);
//                    }
//                }
//            } else if (!(returnType instanceof VoidType)) {
//                // 对于其他类型的返回值，假设为基本类型（例如int, boolean等）
//                ctMethod.insertAfter("com.example.demo.Instrument.sink($_);", true);
//            }

            //直接将this作为参数传递给sink方法，如果是实例方法
//            if (!Modifier.isStatic(method.getModifiers())) {
//                ctMethod.insertAfter("com.example.demo.Instrument.sink2(this);", true);
//            }

//            ctClass.writeFile();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    private int findCalledMethodLineNumber(CtMethod ctMethod, SootMethod calledMethod) {
        try {
            // 获取该方法的字节码指令
            CodeAttribute codeAttribute = ctMethod.getMethodInfo().getCodeAttribute();
            LineNumberAttribute ainfo = (LineNumberAttribute) codeAttribute.getAttribute("LineNumberTable");
            if (ainfo == null) {
                throw new CannotCompileException("no line number info");
            } else {

                for (int i =0; i < ainfo.tableLength(); i++){
                }

                LineNumberAttribute.Pc pc = ainfo.toNearPc(1);
                int lineNum = pc.line;
                int index = pc.index;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1;  // 如果没有找到，返回-1
    }


    private void instrumentThisFields(CtMethod ctMethod) throws NotFoundException, CannotCompileException {
        // 对this对象的字段进行插桩
        CtClass thisClass = ctMethod.getDeclaringClass();
        CtField[] fields = thisClass.getDeclaredFields();

        // 遍历this对象的字段并插入sink
        for (CtField field : fields) {
            String fieldName = field.getName();
            String getterMethodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);  // 生成getter方法名

            try {
                // 通过getter方法访问字段
                CtMethod getterMethod = thisClass.getDeclaredMethod(getterMethodName);
                ctMethod.insertAfter("com.example.demo.Instrument.sink(this." + getterMethod.getName() + "());");
            } catch (Exception e) {
                // 如果没有getter方法，直接访问字段（如果允许）
                ctMethod.insertAfter("com.example.demo.Instrument.sink(this." + fieldName + ");");
            }
        }
    }


    /**
     * 递归生成访问嵌套字段的代码
     * @param str StringBuilder 用于构建插入代码的字符串
     * @param currentObj 当前对象的字符串表示
     * @param currentType 当前对象的类型
     * @param depth 递归深度
     */
    private void generateSinkCalls(StringBuilder str, String currentObj, Type currentType, int depth, AtomicInteger index) {
        if (depth <= 0) {
            return;
        }

        System.out.println("index is : " + index.get());
//        if (index.get() != 1) {
            str.append("com.example.demo.Instrument.sink" + index + "(").append(currentObj).append("); ");
//        }

        index.incrementAndGet();

        if (!(currentType instanceof RefType)) {
            return;
        }

        SootClass sootClass = ((RefType) currentType).getSootClass();
        if (sootClass.getPackageName().startsWith("java.") || sootClass.getPackageName().startsWith("javax.")) {
            return;
        }

        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get(sootClass.getName());

            boolean changePublic = false;
            // 遍历字段并修改修饰符
            for (SootField field : sootClass.getFields()) {
                CtField ctField = ctClass.getDeclaredField(field.getName());
                int currentModifiers = ctField.getModifiers();

                // 如果是 private，替换为 public，保留其他修饰符
                if (Modifier.isPrivate(currentModifiers)) {
                    // 将 private 替换为 public，保留其他修饰符
                    int newModifiers = (currentModifiers & ~Modifier.PRIVATE) | Modifier.PUBLIC;
                    ctField.setModifiers(newModifiers);
                    System.out.println("changed from private to public : " + field + " in " + sootClass.getName());
                    changePublic = true;
                } else if ((currentModifiers & Modifier.PUBLIC) == 0) {
                    // 如果不是 private 且不是 public，添加 public
                    int newModifiers = currentModifiers | Modifier.PUBLIC;
                    ctField.setModifiers(newModifiers);
                    System.out.println("change to public : " + field + " in " + sootClass.getName());
                    changePublic = true;
                }
            }

            if (changePublic){
                System.out.println("there is public change, will save to the new sink jar : " + sootClass.getName());

                // 检查是否是内部类
                if (sootClass.getName().contains("$")) {
                    // 获取外部类的名称
                    String outerClassName = sootClass.getName().substring(0, sootClass.getName().indexOf('$'));
                    SootClass outerClass = Scene.v().getSootClass(outerClassName);
                    // 保存外部类+内部类
                    saveToNewJar(outerClass, generalConfig.sinkClassPath);
                }else{
                    // 保存正常类
                    saveToNewJar(sootClass, generalConfig.sinkClassPath);
                }
            }

            // 再次遍历并使用 CtField 来判断修饰符
            for (SootField field : sootClass.getFields()) {
                CtField ctField = ctClass.getDeclaredField(field.getName());
                // 使用 ctField 的修饰符来判断
                if (Modifier.isPrivate(ctField.getModifiers())) {
                    System.out.println("skip as private : " + field + " in " + sootClass.getName());
                    continue;
                }
                String fieldAccess = currentObj + "." + field.getName();
                generateSinkCalls(str, fieldAccess, field.getType(), depth - 1, index);
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }




}
