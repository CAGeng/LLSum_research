package detect.knowledge.sink.ndaysinks;

import detect.knowledge.sink.HasParamMethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;


@Slf4j
//@Component
public class Fastjsonsinks extends HasParamMethodSinks {
    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();
        SootMethod sootMethod = null;
        try {
            sootMethod = Scene.v().getMethod("<com.alibaba.fastjson.JSON: java.lang.Object parseObject(java.lang.String,java.lang.Class)>");
        } catch (Exception e) {
        }
        if(sootMethod != null) {
            log.error("Fastjson sink method: " + sootMethod.getSignature());
            sinkMethods.add(sootMethod);
            sinkMethodsParams.put(sootMethod, Collections.singletonList(0));
        }

    }

    @Override
    public String getTypeName() {
        return "sink_type_nday";
    }
}
