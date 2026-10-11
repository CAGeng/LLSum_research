package detect.knowledge.sink.xxe;

import detect.knowledge.sink.HasParamMethodSinks;
import detect.knowledge.sink.MethodSinks;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

//@Component
public class XXESinks extends HasParamMethodSinks implements MethodSinks {
    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();

        SootClass sootclass = Scene.v().getSootClassUnsafe("javax.xml.parsers.DocumentBuilder");
        if(sootclass != null) {
            for(SootMethod sootMethod : sootclass.getMethods()) {
                if (sootMethod.getName().equals("parse")) {
                    sinkMethods.add(sootMethod);
                    sinkMethodsParams.put(sootMethod, Collections.singletonList(0));
                }
            }
        }
    }

    @Override
    public String getTypeName() {
        return "sink_type_xxe";
    }
}
