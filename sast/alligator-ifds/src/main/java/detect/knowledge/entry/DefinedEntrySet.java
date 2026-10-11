package detect.knowledge.entry;

import soot.Scene;
import soot.SootMethod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Scanner;

public class DefinedEntrySet extends HasParameterEntrySet {

    public String filePath;

    @Override
    public void detectEntries() {
        try {
            Scanner fileScanner = new Scanner(new FileReader(new File(filePath)));
            while(fileScanner.hasNextLine()){
                String line = fileScanner.nextLine();
                String[] sp = line.split("%%");
                if(sp.length < 2) continue;
                String methodName = sp[0];
                try {
                    //这里做了修改 12-01
                    int ind = Integer.parseInt(sp[1]);
                    SootMethod sootMethod = Scene.v().getMethod(methodName);
                    methodInputMap.put(sootMethod, new ArrayList<>());
                    entries.add(sootMethod);
                    for(int i=1;i<sp.length;++i){
                        methodInputMap.get(sootMethod).add(Integer.parseInt(sp[i]));
                    }

                } catch (Exception ignored){}
            }
        } catch (FileNotFoundException e) {
//            e.printStackTrace();
            System.out.println("[warning] FromFile EntrySet init failed");
        }
    }
}
