package detect.knowledge.entry;

import basic.util.StaticAnalyzeUtils.ClassUtils;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.HashSet;

@Component
public class ElasticSearchEntrySet extends EntrySet{

    public static HashSet<String> requestTypes = new HashSet<>();
    public static HashSet<String> actionClasses = new HashSet<>();
    @Override
    public void detectEntries() {
//        System.out.println("---------------------------  transport actions -----------------------------------------");
        SootClass clazz = Scene.v().getSootClass("org.elasticsearch.action.support.TransportAction");
        if(clazz != null) {
            for(SootClass sootClass1 : ClassUtils.getAllSubs(clazz)) {
                actionClasses.add(sootClass1.toString());
            }
        }

//        System.out.println("---------------------------  transport actions request -----------------------------------------");
        clazz = Scene.v().getSootClass("org.elasticsearch.action.ActionRequest");
        if(clazz != null) {
            for(SootClass sootClass1 : ClassUtils.getAllSubs(clazz)) {
//                System.out.println(sootClass1.toString());
                requestTypes.add(sootClass1.toString());
            }
        }

        clazz = Scene.v().getSootClass("org.elasticsearch.transport.TransportRequest");
        if(clazz != null) {
            for(SootClass sootClass1 : ClassUtils.getAllSubs(clazz)) {
//                System.out.println("[request]  " + sootClass1.toString());
                requestTypes.add(sootClass1.toString());
            }
        }

        for(SootClass sootClass : Scene.v().getClasses()){
            if(sootClass.toString().endsWith("Request")) requestTypes.add(sootClass.toString());
        }

        for(SootClass sootClass : Scene.v().getClasses()){
            if(actionClasses.contains(sootClass.toString())){
                for(SootMethod sootMethod : sootClass.getMethods()){
                    int fl = 0;
                    for(Type type : sootMethod.getParameterTypes()){
                        if(requestTypes.contains(type.toString())) fl = 1;
                    }
                    if(fl == 1){
                        entries.add(sootMethod);
                    }
                }
            }

            for(SootMethod sootMethod : sootClass.getMethods()){
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(requestTypes.contains(type.toString())) fl = 1;
                }
                if(fl == 1){
                    entries.add(sootMethod);
                }
            }

        }
    }
}
