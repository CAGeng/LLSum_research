package edu.fudan.pterosaur.basic.util;

import java.io.*;
import java.util.*;
import java.util.jar.*;

public class JarVersionChecker {

    // Helper function to check if a JAR contains any class compiled with Java 21 (major version 65)
    public static boolean isUpperVersionJavaClass(String jarPath, int javaVersion) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            int checkCount = 0; // 用于记录检查的 .class 文件数量

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    System.out.println("checking : " + entry.getName() + " in " + jarPath);
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        if (isUpperVersionJavaClass(is, javaVersion)) {
                            System.out.println(entry.getName() + " is high , in " + jarPath);
                            return true; // 找到符合条件的文件，直接返回 true
                        }
                        checkCount++; // 增加检查计数
//                        if (checkCount >= 100) {
//                            return false; // 已经检查了5个文件且都不符合条件，返回 false
//                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }


    // Check the major version of a class file
    private static boolean isUpperVersionJavaClass(InputStream classFileStream, int javaVersion) throws IOException {
        DataInputStream dataStream = new DataInputStream(classFileStream);
        dataStream.skipBytes(6); // Skip the first 6 bytes to reach the major version
        int majorVersion = dataStream.readUnsignedShort();
        System.out.println("majorVersion : " + majorVersion);
        return majorVersion >= javaVersion; // 65 indicates Java 21
    }
}