package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;

public class EasyFromFileEntrySet extends EntrySet{
    public String inputFilePath = "/path/to/file.txt";
    @Override
    public void detectEntries() {
        try {
            HashSet<String> methodSet = new HashSet<>();

            // 读取文件并将每行添加到 HashSet
            try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
                String line;
                while ((line = br.readLine()) != null) {
                    methodSet.add(line.trim()); // 去掉行首尾的空白字符
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            for(SootClass sootClass : Scene.v().getClasses()) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(methodSet.contains(sootMethod.getSignature()))
                        entries.add(sootMethod);
                }
            }

        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("[warning] EasyFromFileEntrySet EntrySet init failed");
        }
    }
}
