package detect.knowledge.entry;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class ApacheSentryEntrySet extends EntrySet{
    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getClasses()){
            if(sootClass.getName().equals("SentryPolicyServiceClient")) {
                System.out.println(sootClass);
                for(SootMethod sootMethod : sootClass.getMethods()){
                    System.out.println("     " + sootMethod);
                }
            }
        }
    }
}
