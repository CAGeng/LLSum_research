package app.executor;

import app.configuration.GeneralConfig;
import basic.cg.CG;
import detect.DetectContext;
import detect.knowledge.Knows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.*;
import java.util.stream.Collectors;

/**
 * User: luca
 * Date: 2024/7/9
 * Description:
 */

@Component
public class CodeSplitter {

    @Autowired
    GeneralConfig generalConfig;
    private CG cg;

    private List<DetectContext> detectContexts = new LinkedList<>();

    @Autowired
    Knows knows;

    private List<CodeSegment> codeSegments = new LinkedList<>();

    public void split(){
        List<SootMethod> entries = new ArrayList<>();
        for(SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("com.example.splitting.TestA")){
                for (SootMethod sootMethod : sootClass.getMethods()) {
                    if (sootMethod.getName().equals("main")) {
                        entries.add(sootMethod);
                    }
                }
            }
        }

        if (generalConfig.sparkSwitcher){
            CG.setSpark(true);
        }
        cg = new CG(entries);

        //constructSegmentByStack(entries);
        constructSegment();
    }

    private void constructSegmentByStack(List<SootMethod> entries) {
        knows.expandKnowsAfterCG();
        Set<SootMethod> sinks = knows.getSinks();
        for(SootMethod sinkMethod : sinks) {
            HashMap<SootMethod, SootMethod> methodMapNext = new HashMap<>();
            HashSet<SootMethod> allReachableMethodsToTarget = cg.getAllReachableMethodsToTarget(sinkMethod, methodMapNext);
            for(SootMethod sootMethod : allReachableMethodsToTarget) {
                if(entries.contains(sootMethod)) {
                    List<SootMethod> callStack = new LinkedList<>();
                    Map<String, SootMethod> callStackMap = new HashMap<>();
                    SootMethod it = sootMethod;
                    while(it != null) {
                        callStack.add(it);
                        callStackMap.put(it.getSignature(), it);
                        it = methodMapNext.get(it);
                    }
                    DetectContext detectContext = new DetectContext();
                    detectContext.setEntry(sootMethod);
                    detectContext.setSink(sinkMethod);
                    detectContext.setCallStack(callStack);
                    detectContext.setCallStackMap(callStackMap);
                    detectContext.setKnows(knows);
                    detectContexts.add(detectContext);
                }

            }
        }

        for(DetectContext detectContext : detectContexts){
            SootMethod entrySootMethod = detectContext.getEntry();
            List<CodeSegment> segments = constructSegment(entrySootMethod, 100, 3);
            System.out.println("segments size : " + segments.size());

            Map<String, SootMethod> callStackMap = detectContext.getCallStackMap();

            for (CodeSegment codeSegment : segments){
                List<SootMethod> sootMethods = codeSegment.getSegSootMethods();
                for (SootMethod sootMethod : sootMethods){
                    if (callStackMap.containsKey(sootMethod.getSignature())){
                        System.out.println("method in callStack : " + sootMethod.getName());
                    }
                }
            }
        }
    }


    public List<CodeSegment> constructSegment(SootMethod sootMethod, int depth, int size) {
        List<CodeSegment> codeSegments = new ArrayList<>();
        Map<SootMethod, Integer> methodDepthMap = new LinkedHashMap<>();
        Queue<SootMethod> queue = new LinkedList<>();

        Set<String> sigFilter = new HashSet();
        sigFilter.add("<java.");

        // Start from the initial method
        queue.add(sootMethod);
        methodDepthMap.put(sootMethod, 1);



        while (!queue.isEmpty()) {
            SootMethod currentMethod = queue.poll();
            int currentDepth = methodDepthMap.get(currentMethod);

            if (currentDepth > depth) {
                break;
            }

            // Get all callee methods
            List<SootMethod> calleeSootMethods = cg.calleeOutOfMethod(currentMethod, sigFilter);
            for (SootMethod sMethod : calleeSootMethods){
                if (!methodDepthMap.containsKey(sMethod)) {
                    queue.add(sMethod);
                    methodDepthMap.put(sMethod, currentDepth + 1);
                }
            }
        }

        // Group methods into segments
        Map<Integer, List<SootMethod>> depthSegmentsMap = new HashMap<>();
        for (Map.Entry<SootMethod, Integer> entry : methodDepthMap.entrySet()) {
            SootMethod method = entry.getKey();
            int methodDepth = entry.getValue();

            int segmentIndex = (methodDepth - 1) / size;
            depthSegmentsMap.computeIfAbsent(segmentIndex, k -> new ArrayList<>()).add(method);
        }

        // Create CodeSegments
        for (Map.Entry<Integer, List<SootMethod>> entry : depthSegmentsMap.entrySet()) {
            codeSegments.add(new CodeSegment(entry.getValue()));
        }

        return codeSegments;
    }


    public List<CodeSegment> constructSegment() {
        Set<String> filter = new HashSet<>();
        filter.add("void <init>()>");

        CallGraph callGraph = cg.callGraph;
        // Maps to store in-degree and out-degree of each method
        Map<SootMethod, Integer> inDegreeMap = new HashMap<>();
        Map<SootMethod, Integer> outDegreeMap = new HashMap<>();

        // Initialize in-degree and out-degree
        Scene.v().getApplicationClasses().forEach(sootClass ->
                sootClass.getMethods().forEach(method -> {
                    if (filter.stream().noneMatch(prefix -> method.getSignature().endsWith(prefix))) {
                        inDegreeMap.put(method, 0);
                        outDegreeMap.put(method, 0);
                    }
                })
        );

        // Traverse the call graph to calculate in-degree and out-degree
        callGraph.forEach(edge -> {
            SootMethod src = edge.src();
            SootMethod tgt = edge.tgt();

            // Update out-degree and in-degree
            outDegreeMap.computeIfPresent(src, (k, v) -> v + 1);
            inDegreeMap.computeIfPresent(tgt, (k, v) -> v + 1);
        });

        // Calculate the score for each method
        List<Map.Entry<SootMethod, Double>> sortedScoreList = inDegreeMap.keySet().stream()
                .map(method -> new AbstractMap.SimpleEntry<>(
                        method,
                        Math.round((inDegreeMap.get(method) * 0.7 + outDegreeMap.get(method) * 0.3) * 10.0) / 10.0
                ))
                .sorted((entry1, entry2) -> entry2.getValue().compareTo(entry1.getValue()))
                .collect(Collectors.toList());

        return null;
    }

}
