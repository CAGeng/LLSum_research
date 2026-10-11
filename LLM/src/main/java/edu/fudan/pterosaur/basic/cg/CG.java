package edu.fudan.pterosaur.basic.cg;

import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.*;
import soot.options.Options;
import soot.util.queue.QueueReader;

import java.util.*;
import java.util.function.Function;

/**
 * 调用图（Call Graph）
 *
 * @since 2.0
 */
public class CG {

    public List<SootMethod> entryPoints;//入口方法
    public static LinkedList<String> excludeList;
    public CallGraph callGraph;
    public ReachableMethods reachableMethods;
    public TransitiveTargets transitiveTargets;
    public static Filter filter;

    private static boolean enableSpark = false;

    public static void setSpark(boolean b){
        enableSpark = b;
    }
    public static boolean isSpark() {
        return enableSpark;
    }

    public static boolean mayBeFakeCallByCHA(SootMethod sootMethod) {
        // 这些方法出现在调用栈中，可能是由于CHA带来的误报
        if(sootMethod.getSignature().contains("java.lang.Object next()")) {
            return true;
        }
        List<String> methodNames = Arrays.asList("close", "hasNext");
        for(String badMethod : methodNames) {
            if(sootMethod.getName().equals(badMethod)) return true;
        }

        return false;
    }

    public CG(List<SootMethod> entryPoint){
        // 开始时间
        long startTime = System.nanoTime();
        this.entryPoints =entryPoint;
        System.out.println("constructCG");
        this.callGraph=constructCG();
        System.out.println("constructCG finish");
        this.reachableMethods=Scene.v().getReachableMethods();
        if(filter==null){
            this.transitiveTargets=new TransitiveTargets(callGraph);
        }else {
            this.transitiveTargets=new TransitiveTargets(callGraph,filter);
        }
        // 结束时间
        long endTime = System.nanoTime();
        // 计算耗时
        long duration = endTime - startTime;
        // 打印耗时（以毫秒为单位）
        System.out.println("Creating CG took " + (duration / 1_000_000_000) + " s");
    }

    public CG(SootMethod entryPoint){
        this.entryPoints =Collections.singletonList(entryPoint);
        this.callGraph=constructCG();
        this.reachableMethods=Scene.v().getReachableMethods();
        if(filter==null){
            this.transitiveTargets=new TransitiveTargets(callGraph);
        }else {
            this.transitiveTargets=new TransitiveTargets(callGraph,filter);
        }
    }

    public CG(CallGraph callGraph){
        this.callGraph=callGraph;
        this.reachableMethods=Scene.v().getReachableMethods();
        if(filter==null){
            this.transitiveTargets=new TransitiveTargets(callGraph);
        }else {
            this.transitiveTargets=new TransitiveTargets(callGraph,filter);
        }
    }

    public HashSet<SootMethod> edgesOutOf(SootMethod method){
        HashSet<SootMethod> ret = new HashSet<>();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(method);
        while (edgeIterator.hasNext()) {
            SootMethod invokeMethod = edgeIterator.next().tgt();
            ret.add(invokeMethod);
        }
        return ret;
    }

    public HashSet<SootMethod> edgesInto(SootMethod method){
        HashSet<SootMethod> ret = new HashSet<>();
        Iterator<Edge> edgeIterator = callGraph.edgesInto(method);
        while (edgeIterator.hasNext()) {
            SootMethod invokeMethod = edgeIterator.next().src();
            ret.add(invokeMethod);
        }
        return ret;
    }

    public void setFilter(Filter filter){
//        该Filter用于在查找某指定方法的调用方法时过滤
        CG.filter=filter;
    }

    protected void releaseCallgraph() {
        Scene.v().releaseCallGraph();
        Scene.v().releasePointsToAnalysis();
        Scene.v().releaseReachableMethods();
        G.v().resetSpark();
    }

    private static void enableSparkCallGraph() {
        //Enable Spark
        HashMap<String,String> opt = new HashMap<String,String>();
        opt.put("propagator","worklist");
        opt.put("simple-edges-bidirectional","false");
        opt.put("on-fly-cg","true");
        opt.put("verbose","true");
        opt.put("set-impl","double");
        opt.put("double-set-old","hybrid");
        opt.put("double-set-new","hybrid");
        opt.put("pre_jimplify", "true");
        SparkTransformer.v().transform("",opt);
        PhaseOptions.v().setPhaseOption("cg.spark", "enabled:true");
        PhaseOptions.v().setPhaseOption("cg.spark", "verbose:true");

    }

    private static LinkedList<String> excludeList() {
        if(excludeList==null)
        {
            excludeList = new LinkedList<String> (); // 扩展的基本函数package
            excludeList.add("java.");
            excludeList.add("javax.");
        }
        return excludeList;
    }
    private static void excludeJDKLibrary()
    {
        //exclude jdk classes
        Options.v().set_exclude(excludeList());
        Options.v().set_no_bodies_for_excluded(true);
        Options.v().set_allow_phantom_refs(true);
    }

    private CallGraph constructCG(){
        releaseCallgraph();
        if(enableSpark){
            System.out.println("Enable Spark");
            enableSparkCallGraph();
        }
        excludeJDKLibrary();
        Scene.v().setEntryPoints(this.entryPoints);
        PackManager.v().runPacks();
        return Scene.v().getCallGraph();
    }

    public List<SootMethod> callerIntoMethod(SootMethod method){
//        获取指定方法的所有的调用者
        List<SootMethod> callerList=new ArrayList<>();
        Iterator<Edge> edgeIterator = callGraph.edgesInto(method);
        while (edgeIterator.hasNext()){
            callerList.add(edgeIterator.next().src());
        }
        return callerList;
    }

    public List<SootMethod> calleeOutOfMethod(SootMethod method){
//        获取指定方法的被调用者
        List<SootMethod> calleeList=new ArrayList<>();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(method);
        while (edgeIterator.hasNext()){
            calleeList.add(edgeIterator.next().tgt());
        }
        return calleeList;
    }

    public List<SootMethod> calleeOutOf(Unit unit){
//        获取指定方法的被调用者
        List<SootMethod> calleeList=new ArrayList<>();
        Iterator<Edge> edgeIterator = callGraph.edgesOutOf(unit);
        while (edgeIterator.hasNext()){
            calleeList.add(edgeIterator.next().tgt());
        }
        return calleeList;
    }

    public HashSet<SootMethod> getAllReachableMethodFromEntry(){
//        返回从入口点可达的所有方法
        QueueReader<Edge> listener = callGraph.listener();
        HashSet<SootMethod> allReachableMethod=new HashSet<>();
        while (listener.hasNext()){
            allReachableMethod.add(listener.next().tgt());
        }
        return allReachableMethod;
    }

    // 判断方法是否在CG中
    public boolean isMethodInCG(SootMethod method){
        return reachableMethods.contains(method);
    }

    // 返回指定方法的所有直接或间接调用的方法
    public Iterator<MethodOrMethodContext> getAllMethodsCalledBy(SootMethod method){
        return transitiveTargets.iterator(method);
    }

    public HashSet<SootMethod> findMethodWithFilter(Function<SootMethod, Boolean> filter) {
        HashSet<SootMethod> ret = new HashSet<>();
        for(SootMethod sootMethod : getAllReachableMethodFromEntry()){
            if(filter.apply(sootMethod))
                ret.add(sootMethod);
        }
        return ret;
    }

    public HashSet<SootMethod> getAllReachableMethodsToTarget(SootMethod target){
        HashSet<SootMethod> reachableMethods = new HashSet<>();
        Queue<SootMethod> queue = new LinkedList<>();
        queue.add(target);
        reachableMethods.add(target);
        while (!queue.isEmpty()){
            SootMethod sootMethod = queue.poll();
            for (Iterator<Edge> it = callGraph.edgesInto(sootMethod); it.hasNext(); ) {
                Edge edge = it.next();
                if(!reachableMethods.contains(edge.src())) {
                    reachableMethods.add(edge.src());
                    queue.add(edge.src());
                }
            }
        }
        return reachableMethods;
    }

    // 维护一个method到下一层的映射，以便重现调用栈
    public HashSet<SootMethod> getAllReachableMethodsToTarget(SootMethod target, HashMap<SootMethod, SootMethod> methodMapNext){
        Queue<SootMethod> queue = new LinkedList<>();
        queue.add(target);
        HashSet<SootMethod> reachableMethods = new HashSet<>();
        reachableMethods.add(target);
        while (!queue.isEmpty()){
            SootMethod sootMethod = queue.poll();
            for (Iterator<Edge> it = callGraph.edgesInto(sootMethod); it.hasNext(); ) {
                Edge edge = it.next();
                if(!reachableMethods.contains(edge.src())) {
                    reachableMethods.add(edge.src());
                    // 此时把srcmethod的下一个方法记录为sootMethod
                    methodMapNext.put(edge.src(), sootMethod);
                    queue.add(edge.src());
                }
            }
        }
        return reachableMethods;
    }


    public HashSet<SootMethod> getAllReachableMethodsToTargetSP(SootMethod target, HashMap<SootMethod, List<SootMethod>> methodMapNext){
        Queue<SootMethod> queue = new LinkedList<>();
        queue.add(target);
        HashSet<SootMethod> reachableMethods = new HashSet<>();
        reachableMethods.add(target);
        while (!queue.isEmpty()){
            SootMethod sootMethod = queue.poll();
            for (Iterator<Edge> it = callGraph.edgesInto(sootMethod); it.hasNext(); ) {
                Edge edge = it.next();
                if(!reachableMethods.contains(edge.src())) {
                    reachableMethods.add(edge.src());
                    // 此时把srcmethod的下一个方法记录为sootMethod
                    List<SootMethod> methodList = new LinkedList<>();
                    methodList.add(sootMethod);
                    methodMapNext.put(edge.src(), methodList);
                    queue.add(edge.src());
                }else{
                    List<SootMethod> methodList = methodMapNext.get(edge.src());
                    methodList.add(sootMethod);
                }
            }
        }
        return reachableMethods;
    }


    public List<List<SootMethod>> findCallStacksToMethod(SootMethod entry) {
        List<List<SootMethod>> result = new ArrayList<>();
        Deque<List<SootMethod>> deque = new ArrayDeque<>();

        // 初始化队列
        List<SootMethod> initialPath = new ArrayList<>();
        initialPath.add(entry);
        deque.add(initialPath);

        while (!deque.isEmpty()) {
            System.out.println("queue size : " + deque.size());
            List<SootMethod> currentPath = deque.pollFirst();
            //System.out.println("currentPath :" + currentPath);
            SootMethod currentMethod = currentPath.get(0);

            // 获取所有前驱
            Iterator<Edge> it = callGraph.edgesInto(currentMethod);

            int count = 0;

            if (!it.hasNext() || currentPath.size() > 4) {
                result.add(currentPath);
                System.out.println("find the call stack :" + currentPath);
                continue;
            }

            while (it.hasNext()) {
                count ++;
                Edge edge = it.next();
                SootMethod src = edge.src();
                //System.out.println(src.getSignature() + " count : " + count);
                // 创建新的路径
                List<SootMethod> newPath = new ArrayList<>(currentPath);
                newPath.add(0, src);

                // 添加新的路径到队列
                deque.add(newPath);
            }
        }
        return result;
    }


    public List<String> getAllReachableMethodsSignatureToTarget(SootMethod target){
        List<String> reachableMethods = new LinkedList<>();
        Queue<SootMethod> queue = new LinkedList<>();
        queue.add(target);
//        int t = 0;
        reachableMethods.add(target.getSignature());
        while (!queue.isEmpty()){
            SootMethod sootMethod = queue.poll();
            for (Iterator<Edge> it = callGraph.edgesInto(sootMethod); it.hasNext(); ) {
                Edge edge = it.next();
                if(!reachableMethods.contains(edge.src().getSignature())) {
                    queue.add(edge.src());
                    reachableMethods.add(edge.src().getSignature());
                }
            }
        }
        return reachableMethods;
    }

    public HashSet<SootMethod> getAllReachableMethodsToTarget_withLimit(SootMethod target, int limit) {
        HashSet<SootMethod> allReachable = new HashSet<>();
        recurse(target, allReachable, 0, limit);
        return allReachable;
    }

    private void recurse(SootMethod sootMethod, HashSet<SootMethod> allReachable, int depth, int stack_limit) {

        allReachable.add(sootMethod);
        if(depth < stack_limit) {
            for (SootMethod callerMethod : this.edgesInto(sootMethod)) {
                if(!allReachable.contains(callerMethod)) {
                    recurse(callerMethod, allReachable, depth + 1, stack_limit);
                }
            }
        }

    }
     public HashSet<SootMethod> getAllMethods() {
         Queue<SootMethod> queue = new LinkedList<>();
        HashSet<SootMethod> ans = new HashSet<>();
        for(SootMethod entry : entryPoints){
            queue.add(entry);
        }
        while(!queue.isEmpty()){
            SootMethod tmp = queue.poll();
            if(!ans.contains(tmp)){
                ans.add(tmp);
                HashSet<SootMethod> in = edgesInto(tmp);
                HashSet<SootMethod> out = edgesOutOf(tmp);
                queue.addAll(in);
                queue.addAll(out);
            }
        }
        return ans;
     }

}
