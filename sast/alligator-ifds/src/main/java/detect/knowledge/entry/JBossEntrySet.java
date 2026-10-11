package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

public class JBossEntrySet extends EntrySet{
    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getClasses()){
            if(sootClass.getName().endsWith("Action")) System.out.println(sootClass);
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(type.toString().equals("io.undertow.server.HttpServerExchange")){
                        fl = 1;
                    }
                    if(type.toString().contains("HttpServerExchange")){
                        System.out.println(type);
                        fl = 1;
                    }
                }
                if(sootMethod.getName().equals("call") && sootClass.getName().endsWith("Action")) fl = 1;
                if(sootMethod.getName().equals("handleRequest")) fl = 1;
                if(fl == 1){
                    entries.add(sootMethod);
                }
            }
        }
    }
}
