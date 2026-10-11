package vulexp;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ExtractJarContents {

    public static void main(String[] args) {
        String jarFilePath = "/Users/fortsun/Desktop/test/sca-testcases/arthas-3.7.1/jar-package/arthas-tunnel-server-3.7.1-fatjar.jar";  // 替换为你的 JAR 文件路径

        try (JarFile jarFile = new JarFile(new File(jarFilePath))) {
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if(entry.toString().contains("BOOT-INF") || entry.toString().contains("META-INF")) continue;

                // 获取包名或类名
                String name = entry.getName();

                // 如果是类文件，将类名打印出来
                if (name.endsWith(".class")) {
                    String className = name.replace("/", ".")
                            .substring(0, name.length() - 6);
//                    System.out.println("Class: " + className);
                }

                // 如果是包，将包名打印出来
                if (entry.isDirectory()) {
                    if(entry.toString().contains("BOOT-INF")) continue;
                    if (hasSubdirectories(jarFile, entry)) {
                        // 如果有子目录，跳过
                        continue;
                    }

                    String packageName = name.replace("/", ".");
                    System.out.println("Package: " + packageName);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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
