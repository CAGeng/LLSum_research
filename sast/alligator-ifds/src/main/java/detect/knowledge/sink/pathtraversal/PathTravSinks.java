package detect.knowledge.sink.pathtraversal;

import detect.knowledge.sink.HasParamMethodSinks;
import detect.knowledge.sink.MethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.HashMap;
import java.util.LinkedList;

//@Component
@Slf4j
public class PathTravSinks extends HasParamMethodSinks implements MethodSinks {
    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();
        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("org.springframework.util.FileCopyUtils")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("copyToByteArray")) {
                        addMethodAndParam(sootMethod.getSignature(), 0);
                    }
                }
            }

            if (sootClass.getName().equals("java.io.File")) {
                log.error(sootClass.toString());
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("exists")) {
                        log.error(sootMethod.getSignature());
                    }
                }
            }
        }

        addMethodAndParam("<java.io.File: boolean exists()>", -1);

        log.warn("[Sinks] PathTraversal: " + sinkMethods.size());
    }

    @Override
    public String getTypeName() {
        return "sink_type_pathtraversal";
    }
}
