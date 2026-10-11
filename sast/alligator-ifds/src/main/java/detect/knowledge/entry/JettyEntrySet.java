package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

public class JettyEntrySet extends EntrySet{
    @Override
    public void detectEntries() {
//        for(SootClass sootClass : Scene.v().getClasses()){
//            if(sootClass.getName().toLowerCase(Locale.ROOT).endsWith("request")) System.out.println(sootClass);
//
//        }

        int con = 0;
        for(SootClass sootClass : Scene.v().getClasses()){
            for(SootMethod sootMethod : sootClass.getMethods()){
                con++;
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(type.toString().equals("org.eclipse.jetty.server.Request")){
                        fl = 1;
                    }
                }
                if(fl == 1){
                    entries.add(sootMethod);
                }
            }
        }
        System.out.println("[JettyEntrySet] methods size " + con);
    }
}
