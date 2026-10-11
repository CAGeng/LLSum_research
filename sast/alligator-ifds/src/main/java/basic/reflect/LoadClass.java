package basic.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;
import java.util.List;

public class LoadClass {

    public static String additionalPath = "";
    public URLClassLoader urlClassLoader;

    public LoadClass(String classPath) {
        this.additionalPath = classPath;

        try {
            // 创建一个 URL 对象，表示要添加的路径
            URL additionalUrl = new URL("file://" + additionalPath);

            // 获取当前线程的上下文类加载器
            ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();

            // 创建一个新的 URLClassLoader，将 additionalUrl 添加到路径中
            urlClassLoader = new URLClassLoader(new URL[]{additionalUrl}, parentClassLoader);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Class<?> tryLoad(String className){
        try {
            Class<?> claz = urlClassLoader.loadClass(className);
            return claz;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public List<String> getUsedGenericTypes(Field field) {
        List<String> usedGenericTypes = new LinkedList<>();
        Type fieldType = field.getGenericType();

        if (fieldType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) fieldType;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();

            // typeArguments 包含了泛型的类型信息
            for(Type type : typeArguments) {
                usedGenericTypes.add(type.getTypeName());
            }
        }

        return usedGenericTypes;

    }

    public static void main(String[] args) {
        // 要添加到类加载器路径的目录或文件的路径
        String additionalPath = "/Users/fortsun/Downloads/题目2：源码自动化审计与自动化评估验证/赛题材料/micro_service_seclab-main/out/artifacts/micro_service_seclab_jar/micro-service-seclab.jar";

        try {
            // 创建一个 URL 对象，表示要添加的路径
            URL additionalUrl = new URL("file://" + additionalPath);

            // 获取当前线程的上下文类加载器
            ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();

            // 创建一个新的 URLClassLoader，将 additionalUrl 添加到路径中
            URLClassLoader urlClassLoader = new URLClassLoader(new URL[]{additionalUrl}, parentClassLoader);

            // 现在你可以使用 urlClassLoader 加载类
            Class<?> claz = urlClassLoader.loadClass("com.l4yn3.microserviceseclab.data.Student2");

            // 这里可以使用加载的类进行操作
            // ...
            Field[] fields = claz.getDeclaredFields();

            // 遍历并打印字段信息
            for (Field field : fields) {
                String fieldName = field.getName();
                String fieldType = field.getType().getName();
                // 获取字段的泛型类型
                Type fieldType2 = field.getGenericType();

                if (fieldType2 instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = (ParameterizedType) fieldType2;
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();

                    // typeArguments[0] 包含了泛型的类型信息
                    if (typeArguments.length > 0) {
                        Type genericType = typeArguments[0];
                        System.out.println("Generic Type: " + genericType.getTypeName());
                    }
                }

                System.out.println("Field Name: " + fieldName);
                System.out.println("Field Type: " + fieldType);
            }

            // 不再需要时，记得关闭类加载器以释放资源
            urlClassLoader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class SingletonHolder {
        private static final LoadClass INSTANCE = new LoadClass(additionalPath);
    }

    public static LoadClass getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
