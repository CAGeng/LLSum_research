package detect;

import app.configuration.GeneralConfig;
import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.DefaultDetector.MethodSummary;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.cg.CG;
import basic.cg.CGOptimizer;
import basic.util.LogUtil;
import basic.util.StaticAnalyzeUtils.FieldUtil;
import detect.dataflow.DataFlowSummarize;
import detect.dataflow.DataFlowTrack;
import detect.knowledge.Knows;
import detect.knowledge.entry.ElasticSearchEntrySet;
import detect.knowledge.entry.spring.SpringEntrySet;
import detect.knowledge.entry.tomcat.ConnectorEntrySet;
import detect.knowledge.entry.tomcat.ContainerEntrySet;
import detect.knowledge.sink.ConditionalSinks;
import detect.unit.instrument.InstrumentUtil;
import detect.unit.instrument.Instruments;
import intelligent.IntelligentLogger;
import intelligent.SourceCodeUtil;
import intelligent.datacollect.MethodData;
import intelligent.datacollect.TaintFactData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.jimple.FieldRef;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

@Component
@Scope("prototype")
@Slf4j
public class Summarizer {

    private CG cg;
    private List<DetectContext> detectContexts = new LinkedList<>();

    @Autowired
    SpringEntrySet springEntrySet;

    @Autowired
    GeneralConfig generalConfig;

    public HashSet<FieldRef> allParamFieldRefs = new HashSet<>();
    public HashSet<Type> relatedTypes;
    FileWriter writer = null;

    public void detect() {

        ContainerEntrySet containerEntrySet = new ContainerEntrySet();
        List<SootMethod> entries = containerEntrySet.forceRefreshEntries();
        System.out.println("entry数量： " + entries.size());
        List<SootMethod> subEntries = entries.subList(11, 11);

        String mName = "associate";
        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if (sootMethod.getName().equals(mName)) {
                    subEntries.add(sootMethod);
                }
            }
        }

        // detect field
//        for(SootMethod entry : subEntries) {
//            allParamFieldRefs = new HashSet<>();
//            detectMethod("no-use", "no-use", entry, new HashSet<>());
//            System.out.println("所有参数相关的field引用");
//            logFile( "\n" + entry + "所有参数相关的field引用--------------" + "个数： " + allParamFieldRefs.size());
//            for(FieldRef fieldRef : allParamFieldRefs) {
//                System.out.println(fieldRef);
//                logFile(fieldRef.toString());
//            }
//        }

//        detectMethod("AuthConfigFactoryImpl", "getConfigProvider", null, new HashSet<>());
//        System.out.println("所有参数相关的field引用");
//        logFile("所有参数相关的field引用");
//        for(FieldRef fieldRef : allParamFieldRefs) {
//            System.out.println(fieldRef);
//            logFile(fieldRef.toString());
//        }

        cg = new CG(subEntries);
        List<MethodSummary> summaries = new LinkedList<>();
        for(SootMethod entry : subEntries) {
            log.error("------------------ " + "summarize entry: " + entry + " ------------------");
            DataFlowSummarize dataFlowSummarize = new DataFlowSummarize(entry, cg, Arrays.asList(0));
            dataFlowSummarize.summarizeDataflow();
            MethodDescriptor descriptor = MethodDescriptor.getOrCreateDescriptor(entry);
            summaries.add(descriptor.methodSummary);
        }
        LogUtil.outputToJSON(summaries, generalConfig.sootOutputPath, "summaries.json");
        onDestroy();
    }

    public void detectMethod(String className, String methodName, SootMethod toDetectThisMethod, HashSet<SootMethod> visited) {
        SootMethod entry = null;
        if(toDetectThisMethod != null) entry = toDetectThisMethod;
        else {
            for (SootClass sootClass : Scene.v().getClasses()) {
                if (sootClass.getName().contains(className)) {
                    for (SootMethod sootMethod : sootClass.getMethods()) {
                        if (sootMethod.getName().equals(methodName)) {
                            System.out.println(sootMethod);
                            entry = sootMethod;
                        }
                    }
                }
            }
        }
        if(entry == null) return;
        System.out.println("entry: " + entry);
        logFile("entry: " + entry);
        cg = new CG(entry);
        CFG cfg = new CFG(entry, true).buildCFG();
        List<FieldRef> paramFieldRefs = FieldUtil.getParamFieldRef(entry, cfg);
        System.out.println("======================= " + entry.getName() + " fieldref " + "======================");
//        logFile("======================= " + entry.getName() + " fieldref " + "======================");
        for(FieldRef fieldRef : paramFieldRefs) {
            System.out.println(fieldRef);
//            logFile(fieldRef.toString());
        }
        allParamFieldRefs.addAll(paramFieldRefs);
        List<Node> callStmts = FieldUtil.getCallStmts(entry, cg, cfg);
        System.out.println("======================= " + entry.getName() + " callstmt " + "======================");
        for(Node node : callStmts) {
            System.out.println(node);
        }
        System.out.println("======================= " + entry.getName() + " callees " + "======================");
//        logFile("======================= " + entry.getName() + " callees " + "======================");
        for(Node node : callStmts) {
            List<SootMethod> callees = cg.calleeOutOf(node.unit);
            if(callees.size() > 3) {
                System.out.println("不确定的调用, 跳过： " + node);
//                logFile("不确定的调用, 跳过： " + node);
                continue;
            }
            for(SootMethod sootMethod : callees) {
                System.out.println(sootMethod);
                logFile(sootMethod.toString());
                if(visited.contains(sootMethod)) continue;
                visited.add(sootMethod);
                if(sootMethod.getDeclaringClass().getName().startsWith("org.apache")) {
                    detectMethod("no-use", "no-use", sootMethod, visited);
                }
            }
        }
    }

    @PostConstruct
    public void init() {
        try {
            File directory = new File(generalConfig.getSootOutputPath(), "/logs");
            // 检查目录是否存在
            if (!directory.exists()) {
                // 目录不存在，创建它
                directory.mkdirs();
            }
            writer = new FileWriter(new File(generalConfig.getSootOutputPath(), "/logs/summary-field.txt"));
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    @PreDestroy
    public void onDestroy(){
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void logFile(String s) {
        try {
            writer.write(s + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
