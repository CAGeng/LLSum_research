package edu.fudan.pterosaur.basic.util;

import org.reflections.Reflections;

import java.util.Set;

public class InterfaceImplementationsFinder {

    public static Set<Class<?>> findImplementations(String interfaceName, String basePackage) {
        try {
            // 加载接口的类
            Class<?> interfaceClass = Class.forName(interfaceName);

            // 使用 Reflections 库扫描包
            Reflections reflections = new Reflections(basePackage);

            // 查找所有实现该接口的类
            return reflections.getSubTypesOf((Class<Object>) interfaceClass);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return Set.of(); // 返回空集
        }
    }

    public static void main(String[] args) {
        // 示例：查找 com.rabbitmq.client.ShutdownNotifier 的实现类
        String interfaceName = "com.rabbitmq.client.ShutdownNotifier";
        String basePackage = "com.rabbitmq.client";

        Set<Class<?>> implementations = findImplementations(interfaceName, basePackage);

        System.out.println("实现类：");
        implementations.forEach(impl -> System.out.println(impl.getName()));
    }
}
