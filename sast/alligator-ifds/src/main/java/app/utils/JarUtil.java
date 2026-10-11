package app.utils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class JarUtil {
    public static List<String> getPackageNames(String jarFilePath) {
        List<String> ret = new LinkedList<>();

        try (JarFile jarFile = new JarFile(new File(jarFilePath))) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.toString().contains("BOOT-INF") || entry.toString().contains("META-INF")) continue;

                // 获取包名或类名
                String name = entry.getName();

                // 如果是类文件，将类名打印出来
                if (name.endsWith(".class")) {
                    String className = name.replace("/", ".").substring(0, name.length() - 6);
                    if(className.lastIndexOf('.') == -1) continue;
                    // 判断该类所在的包是否已经添加到结果中，如果没有则添加
                    String packageName = className.substring(0, className.lastIndexOf('.'));
                    if (!ret.contains(packageName)) {
                        ret.add(packageName);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ret;
    }

    private static boolean hasSubdirectories(JarFile jarFile, JarEntry entry) {
        Enumeration<JarEntry> entries = jarFile.entries();
        String parentEntryName = entry.getName();

        while (entries.hasMoreElements()) {
            JarEntry subEntry = entries.nextElement();
            if (subEntry.getName().startsWith(parentEntryName) &&
                    subEntry.getName().length() > parentEntryName.length() &&
                    subEntry.getName().indexOf("/", parentEntryName.length() + 1) != -1) {
                return true; // 存在子目录
            }
        }
        return false; // 没有子目录
    }
}
