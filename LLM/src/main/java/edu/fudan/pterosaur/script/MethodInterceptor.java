package edu.fudan.pterosaur.script;

import javassist.*;

public class MethodInterceptor {

    public static void main(String[] args) throws Exception {
        modifyMethod("com.example.TestClass", "encryptorTest");
    }

    /**
     * 修改指定类中的指定方法，插入打印参数实现类的代码
     *
     * @param className  类名
     * @param methodName 方法名
     */
    public static void modifyMethod(String className, String methodName) throws Exception {
        // 获取ClassPool，作为所有类的工厂
        ClassPool pool = ClassPool.getDefault();
        
        // 获取指定的类
        CtClass cc = pool.get(className);

        // 获取方法
        CtMethod method = cc.getDeclaredMethod(methodName);
        
        // 遍历方法参数
        CtClass[] parameterTypes = method.getParameterTypes();
        StringBuilder beforeCode = new StringBuilder();

        // 插入打印参数实现类的代码
        for (int i = 0; i < parameterTypes.length; i++) {
            CtClass paramType = parameterTypes[i];
            if (paramType.isInterface()) {
                // 如果参数类型是接口类型，则打印其具体实现类
                beforeCode.append("System.out.println(\"Parameter " + (i + 1) + " is an interface. Actual implementation: \" + $")
                        .append(i + 1)
                        .append(".getClass().getName());\n");
            }
        }

        // 将插入的代码放到方法的开头
        method.insertBefore(beforeCode.toString());

        // 修改后的字节码类
        cc.toClass(); // 使修改生效

        System.out.println("Method " + methodName + " in class " + className + " has been modified.");
    }
}
