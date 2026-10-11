package basic.reflect;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

/**
 * Bridge: soot和java反射两方案之间的转换
 */
public class Bridge {

    public static Method tryGetMethod(String className, String subSignature, ClassLoader classLoader) {
        // 示例 Soot 方法签名
        String sootMethodSignature = subSignature;

        // 解析 Soot 方法签名
        String[] parts = sootMethodSignature.split(" ", 2);
        String returnType = parts[0];
        String methodNameWithParams = parts[1];

        // 获取方法名和参数列表
        int openParenthesisIndex = methodNameWithParams.indexOf('(');
        String methodName = methodNameWithParams.substring(0, openParenthesisIndex);
        String params = methodNameWithParams.substring(openParenthesisIndex + 1, methodNameWithParams.length() - 1);

        // 获取参数类型列表
        String[] paramTypes = params.split(",");
        Class<?>[] parameterTypes = new Class<?>[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            String paramType = paramTypes[i].trim();
            if ("int".equals(paramType)) {
                parameterTypes[i] = int.class;
            } else if ("char".equals(paramType)) {
                parameterTypes[i] = char.class;
            } else if ("boolean".equals(paramType)) {
                parameterTypes[i] = boolean.class;
            } else {
                try {
                    parameterTypes[i] = Class.forName(paramType);
                } catch (ClassNotFoundException e) {
                }
            }
        }

        // 使用反射获取方法
        try {
            Class<?> clazz = classLoader.loadClass(className);
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            return method;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) throws NoSuchMethodException {
        // 示例 Soot 方法签名
        String sootMethodSignature = "void myMethod(java.lang.String, int)";

        // 解析 Soot 方法签名
        String[] parts = sootMethodSignature.split(" ", 2);
        String returnType = parts[0];
        String methodNameWithParams = parts[1];

        // 获取方法名和参数列表
        int openParenthesisIndex = methodNameWithParams.indexOf('(');
        String methodName = methodNameWithParams.substring(0, openParenthesisIndex);
        String params = methodNameWithParams.substring(openParenthesisIndex + 1, methodNameWithParams.length() - 1);

        // 获取参数类型列表
        String[] paramTypes = params.split(",");
        Class<?>[] parameterTypes = new Class<?>[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            String paramType = paramTypes[i].trim();
            System.out.println(paramType);
            if ("int".equals(paramType)) {
                parameterTypes[i] = int.class;
            } else if ("char".equals(paramType)) {
                parameterTypes[i] = char.class;
            } else if ("boolean".equals(paramType)) {
                parameterTypes[i] = boolean.class;
            } else {
                try {
                    parameterTypes[i] = Class.forName(paramType);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        // 获取方法的 Class 对象
        Class<?> clazz = MyClass.class; // 替换为包含该方法的类的 Class 对象

        // 使用反射获取方法
        try {
            System.out.println(Arrays.toString(parameterTypes));
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);

            // 打印方法信息
            System.out.println("Method Name: " + method.getName());
            System.out.println("Return Type: " + method.getReturnType().getName());
            System.out.println("Modifiers: " + Modifier.toString(method.getModifiers()));
            for (Class<?> paramType : method.getParameterTypes()) {
                System.out.println("Parameter Type: " + paramType.getName());
            }
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    class MyClass {
        public void myMethod(String strParam, int intParam) {
            // 方法实现
        }
    }
}

