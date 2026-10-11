package detect.knowledge.sink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

//@Component
@Slf4j
public class MethodSinksRCE extends HasParamMethodSinks implements MethodSinks{

    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();
        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("java.lang.Runtime")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("exec")) {
                        sinkMethods.add(sootMethod);
                        sinkMethodsParams.put(sootMethod, Collections.singletonList(0));
                    }
                }
            }
        }

        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("java.lang.ProcessBuilder")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("<init>")) {
                        sinkMethods.add(sootMethod);
                        sinkMethodsParams.put(sootMethod, Collections.singletonList(0));
                    }
                }
            }
        }

        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("org.springframework.expression.spel.standard.SpelExpression")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("getValue")) {
                        sinkMethods.add(sootMethod);
                    }
                }
            }
        }

        log.warn("[Sinks] runShellMethods: " + sinkMethods.size());
    }

    @Override
    public String getTypeName() {
        return "sink_type_rce";
    }
}
