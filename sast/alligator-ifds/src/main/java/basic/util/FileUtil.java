package basic.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

public class FileUtil {
    public static List<String> readlines(String filePath){
        List<String> ret = new LinkedList<>();
        Scanner fileScanner = null;
        try {
            fileScanner = new Scanner(new FileReader(new File(filePath)));
        } catch (FileNotFoundException e) {
            return ret;
        }
        while(fileScanner.hasNextLine()){
            String line = fileScanner.nextLine();
            ret.add(line);
        }
        return ret;
    }

    public static List<File> listFiles(File directory) {

        List<File> ret = new LinkedList<>();
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    // 如果是子目录，递归遍历子目录
                    ret.addAll(listFiles(file)) ;
                } else {
                    // 如果是文件，输出文件路径
                    ret.add(file);
                }
            }
        }

        return ret;
    }


    public static List<String> getJarFilesInFolder(String folderPath){
        return getFilesInFolder(folderPath, ".jar");
    }

    public static List<String> getFilesInFolder(String folderPath, String suffix){
        List<String> jarFilePaths = new ArrayList<>();
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(suffix)) {
                        jarFilePaths.add(file.getAbsolutePath());
                    }
                }
            }
        }

        return jarFilePaths;
    }
}
