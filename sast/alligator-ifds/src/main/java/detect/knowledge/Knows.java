package detect.knowledge;

import app.configuration.SootConfig;
import app.context.SpringContextUtils;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.knowledge.entry.webapp.ServletEntrySet;
import detect.knowledge.sink.ConditionalSinks;
import detect.knowledge.sink.MethodSinksCollection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;


@Component
//@Scope("prototype") // knows不要多次创建，否则expandAfterCG会无法被触发
@Getter
@Setter
@Slf4j
public class Knows{

    @Autowired
    SootConfig sootConfig;

    @Autowired
    SpringEntrySet springEntrySet;

    @Autowired
    ServletEntrySet servletEntrySet;

    @Autowired
    MethodSinksCollection collection;

    private Set<SootMethod> sinks = new HashSet<>();

    private HashMap<SootMethod, List<Integer>> sinksParams = new HashMap<>();

    private HashMap<String, Element> conditional = new HashMap<>();

    private List<SootMethod> entries;
    public HashMap<SootMethod, List<Integer>> methodInputMap = new HashMap<>(); // 指定污染源头的参数

    private Map<String, List<SootMethod>> sinkTypes = new HashMap<>();


    @PostConstruct
    @ConditionalOnNotWebApplication
    private void init(){
        entries = springEntrySet.getEntries();
        entries.addAll(servletEntrySet.getEntries());
    }

    /**
     * Expand the sink methods after the call graph is built.
     */
    public void expandKnowsAfterCG() {

        collection.getMethodSinksMap().forEach((key, methodSinks) -> {

//            log.warn("using rule: " + methodSinks.getTypeName() + "  in class: " + methodSinks.getClass().getName());
//            if(!methodSinks.getTypeName().equals("sink_type_generic")) {
//                log.error("!! [只用了配置文件中规则，为了测试] ！！！！");
//                return;
//            }

            List<SootMethod> sootMethodList = methodSinks.SinkMethodsAfterForceSync();
            //todo. Risk of coverage（the same one is defined in two places）
            sinks.addAll(sootMethodList);
            sinksParams.putAll(methodSinks.sinkMethodsParams());

            HashMap<String, Element> conditionalEle = methodSinks.getConditional();
            if (conditionalEle != null){
                for (Map.Entry<String, Element> entry : conditionalEle.entrySet()) {
                    String signature = entry.getKey();
                    if (conditional.containsKey(signature)){
                        continue;
                    }
                    Element ele = entry.getValue();
                    conditional.put(signature, ele);
                }
            }

            String sinkTypeName = methodSinks.getTypeName();
            if (sinkTypes.containsKey(sinkTypeName)){
                sinkTypes.get(sinkTypeName).addAll(methodSinks.sinkMethods());
            }else{
                sinkTypes.put(sinkTypeName,methodSinks.sinkMethods());
            }
        });

        // 清除一些规则中不好的sink
        clearBadSinks();

    }

    // 清除一些规则中不好的sink
    public void clearBadSinks() {
        List<String> badSinks = Arrays.asList(
                "<java.lang.Class: java.lang.ClassLoader getClassLoader()>",
                "<java.io.PrintWriter: void println(java.lang.String)>",
                "<java.io.PrintStream: void println(java.lang.String)>",
                "<java.lang.String: boolean equals(java.lang.Object)>",
                "<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.Object)>",
                "<java.lang.Throwable: void printStackTrace(java.io.PrintStream)>",
                "<java.io.PrintWriter: void write(java.lang.String)>",
                "<java.lang.Throwable: void printStackTrace(java.lang.Throwable$PrintStreamOrWriter)>",
                "<java.util.logging.Logger: java.util.logging.Logger getLogger(java.lang.String)>"
        );

        Set<SootMethod> newSinks = new HashSet<>();
        for(SootMethod sink : sinks) {
            if(!badSinks.contains(sink.getSignature())) {
                newSinks.add(sink);
            }
        }
        sinks = newSinks;
    }


    @PreDestroy
    @ConditionalOnWebApplication
    public void preDestroy(){
        log.info("Destroy Knows when it's a WebApplication");
    }

}
