package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.Locale;

public class EasyEntrySet extends EntrySet{
    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getApplicationClasses()){
            if(sootClass.toString().startsWith("java")) continue;
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    String s = type.toString();
                    if(s.toLowerCase(Locale.ROOT).endsWith("request") || s.toLowerCase(Locale.ROOT).endsWith("query")){
                        fl = 1;
                    }
                }
                if(fl == 1){
                    entries.add(sootMethod);
                }
//                if(sootMethod.toString().endsWith("void main(java.lang.String[])>")){
//                    entries.add(sootMethod);
//                }
            }
        }
    }
}
