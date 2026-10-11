package edu.fudan.pterosaur.script;

import javassist.*;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class AutoInvoker {


    static ExecutorService executorService = Executors.newFixedThreadPool(10); // 限制线程池大小

    public static void main(String[] args) {
        String[] jarFilePaths = {
//                "/Users/luca/dev/2024/pangaea/rosefinch/target/rosefinch-0.0.1-SNAPSHOT.jar",
//                "/Users/luca/dev/2024/pangaea/rosefinch/target/rosefinch-0.0.1-SNAPSHOT-tests.jar",
//                "/Users/luca/dev/2025/pilot/hutool/hutool-core/target/hutool-core-5.8.33.jar",
//                "/Users/luca/dev/2025/pilot/hutool/hutool-core/target/hutool-core-5.8.33-tests.jar"
                "/Users/luca/dev/2025/pilot/fastjson/target/fastjson-1.2.84-SNAPSHOT.jar",
                "/Users/luca/dev/2025/pilot/fastjson/target/fastjson-1.2.84-SNAPSHOT-tests.jar"
        };

        instrument(jarFilePaths);
        invoke(jarFilePaths);
    }

    private static void instrument(String[] jarFilePaths) {
        // 创建ClassPool并将JAR文件添加到ClassPool
        ClassPool pool = ClassPool.getDefault();
        try {
            for (int i = 0; i < jarFilePaths.length; i++) {
                pool.appendClassPath(jarFilePaths[i]); // 加载JAR文件路径
            }
            // 处理JAR文件中的所有类
            processJarFile(jarFilePaths[1], pool);
        } catch (NotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void processJarFile(String jarFilePath, ClassPool pool) {
        File jarFile = new File(jarFilePath);
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            // 创建临时JAR文件输出流
            File tempJarFile = new File(jarFile.getParent(), "temp_" + jarFile.getName());
            try (JarOutputStream outputStream = new JarOutputStream(new FileOutputStream(tempJarFile))) {
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (name.endsWith(".class")) {
                        String className = name.replace('/', '.').replace(".class", "");
                        try {
                            CtClass ctClass = pool.get(className);
                            // 查找带有 @Test 注解的方法并修改其body
                            findTestMethods(ctClass);
                            // 将修改后的字节码写回到临时JAR文件中
                            writeModifiedClassToJar(ctClass, className, outputStream);
                        } catch (NotFoundException e) {
                            // 如果类找不到，跳过
                            System.out.println("Class not found: " + name);
                        }
                    } else {
                        // 其他非.class文件直接复制到新的JAR文件
                        copyJarEntryToNewJar(entry, jar, outputStream);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 删除原JAR并重命名临时文件
            if (jarFile.delete()) {
                tempJarFile.renameTo(jarFile);
            } else {
                System.out.println("Failed to replace original JAR file.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeModifiedClassToJar(CtClass ctClass, String className, JarOutputStream outputStream) {
        try {
            // 转换为字节数组并写入到JAR输出流
            byte[] classBytes = ctClass.toBytecode();
            JarEntry entry = new JarEntry(className.replace('.', '/') + ".class");
            outputStream.putNextEntry(entry);
            outputStream.write(classBytes);
            outputStream.closeEntry();
        } catch (IOException | CannotCompileException e) {
            e.printStackTrace();
        }
    }

    private static void copyJarEntryToNewJar(JarEntry entry, JarFile jar, JarOutputStream outputStream) {
        try (InputStream input = jar.getInputStream(entry)) {
            JarEntry newEntry = new JarEntry(entry.getName());
            outputStream.putNextEntry(newEntry);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.closeEntry();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void findTestMethods(CtClass ctClass) {
        try {
            // 遍历类中的所有方法
            for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
                // 如果方法有 @Test 注解，则修改方法体
                if (ctMethod.hasAnnotation(Test.class)) {
//                    System.out.println("Found @Test method: " + ctMethod.getName());
                    modifyMethod(ctMethod);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void modifyMethodBody(CtMethod ctMethod) throws CannotCompileException {
        // 修改方法体，将其设置为输出 "Luca updated"
        ctMethod.setBody("{ System.out.println(\"Luca updated\"); }");
    }


    private static void modifyMethod(CtMethod ctMethod) throws CannotCompileException {
        // 使用 ExprEditor 修改方法调用表达式
        ctMethod.instrument(new ExprEditor() {
            @Override
            public void edit(MethodCall m) throws CannotCompileException {
                CtClass[] parameterTypes;
                try {
                    parameterTypes = m.getMethod().getParameterTypes();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                    return;
                }

                StringBuilder printCode = new StringBuilder();

                // 遍历所有参数，检查是否是接口类型
                for (int i = 0; i < parameterTypes.length; i++) {
                    String className = parameterTypes[i].getName();
                    if (parameterTypes[i].isInterface() && !isBasicJavaClass(className)) {
                        // 生成打印语句：输出接口类型参数的具体实现类
                        try {
                            printCode.append("System.out.println(\"Parameter ")
                                    .append(i + 1)
                                    .append(" in method ")
                                    .append(m.getMethod().getLongName())
                                    .append(" is an interface. Actual implementation: \" + $")
                                    .append(i + 1)
                                    .append(".getClass().getName());\n");
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }

                // 插入代码
                try {
                    if (m.getMethod().getReturnType().equals(CtClass.voidType)) {
                        // 如果方法没有返回值，直接插入打印语句，并使用 $proceed() 保留原方法调用
                        if (printCode.length() > 0) {
                            String newCode = printCode + "$proceed($$);";  // $$ 表示保留原参数
                            m.replace(newCode);
                        }
                    } else {
                        // 如果方法有返回值，则使用 $_ 来接收返回值
                        if (printCode.length() > 0) {
                            String newCode = printCode + "$_ = $proceed($$);";  // $_ 表示返回值
                            m.replace(newCode);
                        }
                    }
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    // 判断是否是Java基础类
    private static boolean isBasicJavaClass(String className) {
        // 检查是否属于Java标准库的基础类
        return className.startsWith("java.lang.") ||
                className.startsWith("java.util.") ||
                className.startsWith("java.io.") ||
                className.startsWith("javax.") ||
                className.startsWith("sun.");
    }


    private static void invoke(String[] jarFilePaths) {
        // 创建一个URLClassLoader来加载所有JAR文件
        URL[] urls = new URL[jarFilePaths.length];
        for (int i = 0; i < jarFilePaths.length; i++) {
            try {
                urls[i] = new URL("jar:file:" + jarFilePaths[i] + "!/");
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        // 注意：这里我们传递了urls数组和父类加载器（通常是系统类加载器）
        try (URLClassLoader classLoader = new URLClassLoader(urls, AutoInvoker.class.getClassLoader())) {
            for (String jarFilePath : jarFilePaths) {
                processJarFile(jarFilePath, classLoader);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void processJarFile(String jarFilePath, URLClassLoader classLoader) {
        try (JarFile jarFile = new JarFile(new File(jarFilePath))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    String className = name.replace('/', '.').replace(".class", "");
                    try {
                        // 使用类加载器加载类
                        if (className.endsWith("Test")){
                            Class<?> clazz = Class.forName(className, true, classLoader);
                            executeTestMethods(clazz);
                        }
                    } catch (ClassNotFoundException e) {
                        System.out.println("Class not found: " + className);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void executeTestMethods(Class<?> clazz) {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {

            method.setAccessible(true);
            Annotation[] annotations = method.getDeclaredAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof Test) {


                    while (Thread.activeCount() > 300) {
                        System.out.println("Too many active threads, sleep 10s...");
                        try {
                            Thread.sleep(10000);
                            if (Thread.activeCount() > 300) {
                                System.out.println("Too many active threads again, sleep");
                                ThreadGroup group = Thread.currentThread().getThreadGroup();
                                while (group.getParent() != null) {
                                    group = group.getParent(); // 找到根线程组
                                }
                                Thread[] threads = new Thread[group.activeCount()];
                                group.enumerate(threads); // 获取所有线程

                                for (Thread thread : threads) {
                                    if (thread != null && thread != Thread.currentThread()) {
                                        System.out.println("Interrupting thread: " + thread.getName());
                                        thread.interrupt(); // 尝试中断线程
                                    }
                                }
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }



                    Future<?> future = executorService.submit(() -> {
                        try {
                            method.invoke(clazz.getDeclaredConstructor().newInstance());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    try {
                        future.get(10, TimeUnit.SECONDS); // 等待10秒超时
                    } catch (TimeoutException e) {
                        System.out.println("Timeout! Task cancelled.");
                        future.cancel(true); // 超时后取消任务
                    } catch (Exception e) {
                        e.printStackTrace();
                        future.cancel(true);
                    }
                }
            }
        }
    }


    private static void interruptThreadGroup(ThreadGroup group) {
        Thread[] threads = new Thread[group.activeCount()];
        group.enumerate(threads);
        for (Thread thread : threads) {
            thread.interrupt(); // 中断每个线程
        }
    }


    // 获取方法签名，模拟Soot的格式
    private static String getSootMethodSignature(Method method) {
        // Soot-style format: <fully.qualified.ClassName: returnType methodName(paramTypes)>
        String className = method.getDeclaringClass().getName(); // Fully qualified class name
        String returnType = method.getReturnType().getName(); // Return type
        String methodName = method.getName(); // Method name

        // Get parameter types and format them similarly to Soot's way
        Class<?>[] parameterTypes = method.getParameterTypes();
        StringBuilder paramTypes = new StringBuilder();
        for (Class<?> paramType : parameterTypes) {
            // Format each parameter type similar to Soot's style (fully qualified name with a trailing ';')
            paramTypes.append(paramType.getName().replace('.', '/') + ";");
        }

        return String.format("<%s: %s %s(%s)>", className, returnType, methodName, paramTypes.toString());
    }
}