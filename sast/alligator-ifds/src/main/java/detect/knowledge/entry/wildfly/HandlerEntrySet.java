package detect.knowledge.entry.wildfly;

import basic.util.StaticAnalyzeUtils.ClassUtils;
import detect.knowledge.entry.HasParameterEntrySet;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.HashSet;
import java.util.LinkedList;

public class HandlerEntrySet extends HasParameterEntrySet {
    @Override
    public void detectEntries() {
        try {
            SootClass sootClass = Scene.v().getSootClassUnsafe("io.undertow.server.HttpHandler");
            if (sootClass != null) {
                HashSet<SootClass> handlerClasses = ClassUtils.getAllSubs(sootClass);
                for (SootClass handlerClass : handlerClasses) {
                    for (SootMethod sootMethod : handlerClass.getMethods()) {
                        if (sootMethod.getName().equals("handleRequest")) {
                            entries.add(sootMethod);
                            LinkedList<Integer> taintList = new LinkedList<>();
                            taintList.add(0);
                            methodInputMap.put(sootMethod, taintList);
                        }
                    }
                }

                System.out.println("-- entries -");
                for (SootMethod sootMethod : entries) {
                    System.out.println(sootMethod);
                    System.out.println("    参数， " + sootMethod.getParameterType(0));
                }
                System.out.println();
            }
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("[warning] wildfly Handlers EntrySet init failed");
        }
    }
}
