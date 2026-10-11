package detect.knowledge.sink.ssrf;

import detect.knowledge.sink.HasParamMethodSinks;
import detect.knowledge.sink.MethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
//@Component
public class SSRFSinks extends HasParamMethodSinks implements MethodSinks {
    @Override
    public void initSinkMethods() {

        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();

        try {
            SootMethod sink1 = Scene.v().getMethod("<java.net.URL: java.net.URLConnection openConnection()>");
            sinkMethods.add(sink1);
            sinkMethodsParams.put(sink1, Collections.singletonList(-1));
        } catch (Exception e){}
        try {
            SootMethod sink2 = Scene.v().getMethod("<org.apache.http.client.fluent.Request: org.apache.http.client.fluent.Request Get(java.lang.String)>");
            sinkMethods.add(sink2);
            sinkMethodsParams.put(sink2, Collections.singletonList(0));
        } catch (Exception e){}
        try {
            SootMethod sink3 = Scene.v().getMethod("<com.squareup.okhttp.Request$Builder: com.squareup.okhttp.Request$Builder url(java.net.URL)>");
            sinkMethods.add(sink3);
            sinkMethodsParams.put(sink3, Collections.singletonList(0));
        } catch (Exception e){}
        try {
            SootMethod sink4 = Scene.v().getMethod("<org.apache.http.client.methods.HttpGet: void <init>(java.lang.String)>");
            sinkMethods.add(sink4);
            sinkMethodsParams.put(sink4, Collections.singletonList(0));
        } catch (Exception e){}
        try {
            SootMethod sink5 = Scene.v().getMethod("<java.net.URL: java.io.InputStream openStream()>");
            sinkMethods.add(sink5);
            sinkMethodsParams.put(sink5, Collections.singletonList(-1));
        } catch (Exception e){}

        log.warn("ssrf sinks: " + sinkMethods.size());
    }

    @Override
    public String getTypeName() {
        return "sink_type_ssrf";
    }
}
