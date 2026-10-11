package detect.knowledge.entry.tomcat;

import detect.knowledge.entry.EntrySet;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

public class ClusterEntrySet extends EntrySet {
    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getClasses()){
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(type.toString().equals("java.nio.channels.SelectionKey")){
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
