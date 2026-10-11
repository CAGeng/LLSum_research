package edu.fudan.pterosaur.basic.util;

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
                        if (JarVersionChecker.isUpperVersionJavaClass(file.getAbsolutePath(), 65)){
                            System.out.println(" too new version : " + file.getAbsolutePath());
                        }else{
                            System.out.println(" standard class : " + file.getAbsolutePath());
                            jarFilePaths.add(file.getAbsolutePath());
                        }
                    }
                }
            }
        }

        return jarFilePaths;
    }

    // 计算文件夹或文件的总大小
    public static long calculateSize(File file) {
        if (file.isFile()) {
            return file.length();
        }

        long totalSize = 0;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                totalSize += calculateSize(f);
            }
        }
        return totalSize;
    }

    // 比较文件夹或文件大小并计算比例
    public static double calculateSizeRatio(File file1, File file2) {
        long size1 = calculateSize(file1);
        long size2 = calculateSize(file2);

        // 防止除以零
        if (size2 == 0) {
            throw new ArithmeticException("文件或文件夹2的大小为零，无法计算比例");
        }

        return (double) size1 / size2;
    }


    public static double calculateSizeRatio(long size1, long size2) {
        if (size2 == 0) {
            throw new ArithmeticException("文件或文件夹2的大小为零，无法计算比例");
        }
        return (double) size1 / size2;
    }



    public static void main(String[] args) {

    }
}
