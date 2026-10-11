package detect.knowledge.sink;

import basic.DefaultDetector.MethodDescriptor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootMethod;

import java.util.*;

/*
    一些基于更复杂条件的sink
 */
@Setter
@Getter
@Component
@Slf4j
public class ConditionalSinks extends HasParamMethodSinks implements MethodSinks{
    public HashSet<String> conditionalSinks = new HashSet<>();

    public ConditionalSinks() {
        // 一批cve提取到的sink
//        // cve-
//        conditionalSinks.add("<org.apache.catalina.session.StandardSession: void setNote(java.lang.String,java.lang.Object)>");
//        // 测试-
//        conditionalSinks.add("<com.l4yn3.microserviceseclab.controller.common.Dataflow: void conditionSink(java.lang.String,java.lang.String)>");
//        // cve-2018-8014
//        conditionalSinks.add("<org.apache.catalina.filters.CorsFilter: void parseAndStore(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)>");
//        // cve-2014-0075
//        conditionalSinks.add("<org.apache.coyote.http11.filters.ChunkedInputFilter: boolean parseChunkHeader()>");
//        // cve-2012-3544
//        conditionalSinks.add("<org.apache.coyote.http11.filters.ChunkedInputFilter: int doRead(org.apache.tomcat.util.net.ApplicationBufferHandler)>");
        // CVE-2017-12617
//        conditionalSinks.add("<org.apache.catalina.webresources.DirResourceSet: boolean write(java.lang.String,java.io.InputStream,boolean)>");
//        conditionalSinks.add("<org.apache.catalina.webresources.AbstractFileResourceSet: java.io.File file(java.lang.String,boolean)>");
    }

    public boolean conditionValid(String method, MethodDescriptor descriptor) {
        if(method.equals("<org.apache.catalina.session.StandardSession: void setNote(java.lang.String,java.lang.Object)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(-1) && taintedParams.contains(1)) {
                return true;
            }
        }
        if(method.equals("<com.l4yn3.microserviceseclab.controller.common.Dataflow: void conditionSink(java.lang.String,java.lang.String)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(0) && taintedParams.contains(1)) {
                return true;
            }
        }
        if(method.equals("<org.apache.catalina.filters.CorsFilter: void parseAndStore(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(0) && taintedParams.contains(4)) {
                return true;
            }
        }
        if(method.equals("<org.apache.coyote.http11.filters.ChunkedInputFilter: boolean parseChunkHeader()>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(-1)) {
                return true;
            }
        }
        if(method.equals("<org.apache.coyote.http11.filters.ChunkedInputFilter: int doRead(org.apache.tomcat.util.net.ApplicationBufferHandler)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(0)) {
                return true;
            }
        }
        if(method.equals("<org.apache.catalina.webresources.DirResourceSet: boolean write(java.lang.String,java.io.InputStream,boolean)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(0)) {
                return true;
            }
        }
        if(method.equals("<org.apache.catalina.webresources.AbstractFileResourceSet: java.io.File file(java.lang.String,boolean)>")) {
            List<Integer> taintedParams = descriptor.getTaintedParams();
            if(taintedParams.contains(0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
//        try {
//            SootMethod sootMethod = Scene.v().getMethod("<org.apache.catalina.session.StandardSession: void setNote(java.lang.String,java.lang.Object)>");
//            sinkMethods.add(sootMethod);
//            sinkMethodsParams.put(sootMethod, Arrays.asList(-1, 1));
//        } catch (RuntimeException ignored){} // 没找到这个方法
        for(String method : conditionalSinks) {
            try {
                SootMethod sootMethod = Scene.v().getMethod(method);
                sinkMethods.add(sootMethod);
                sinkMethodsParams.put(sootMethod, Arrays.asList(-1, 0, 1, 2, 3, 4, 5, 6)); //先都加进去
            } catch (RuntimeException ignored){} // 没找到这个方法
        }

    }

    @Override
    public String getTypeName() {
        return "condition_sink_self_define";
    }
}
