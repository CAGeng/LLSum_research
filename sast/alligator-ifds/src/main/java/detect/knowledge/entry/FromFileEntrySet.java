package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Scanner;

public class FromFileEntrySet extends EntrySet{

    public String filePath;
    public HashSet<String> paramTypes = new HashSet<>();
    @Override
    public void detectEntries() {
        try {
            Scanner fileScanner = new Scanner(new FileReader(new File(filePath)));
            while(fileScanner.hasNextLine()){
                String paramType = fileScanner.nextLine();
                paramTypes.add(paramType);
            }

        } catch (FileNotFoundException e) {
//            e.printStackTrace();
            System.out.println("[warning] FromFile EntrySet init failed");
        }

        for(SootClass sootClass : Scene.v().getClasses()){
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(paramTypes.contains(type.toString())){
                        fl = 1;
                    }
                }
                if(fl == 1){
                    entries.add(sootMethod);
                }
            }
        }
    }
}
