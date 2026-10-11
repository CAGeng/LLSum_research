package edu.fudan.pterosaur.basic.cg;

import soot.SootMethod;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
public class CallStackAnalyzer {

    private CallGraph cg;
    private SootMethod entry;
    private SootMethod sink;
    private List<List<SootMethod>> allCallStacks;

    public CallStackAnalyzer(CallGraph cg, SootMethod entry, SootMethod sink) {
        this.cg = cg;
        this.entry = entry;
        this.sink = sink;
        this.allCallStacks = new ArrayList<>();
    }

    public List<List<SootMethod>> getAllCallStacks() {
        Stack<SootMethod> currentStack = new Stack<>();
        currentStack.push(entry);
        findCallStacks(entry, currentStack);
        return allCallStacks;
    }

    private void findCallStacks(SootMethod currentMethod, Stack<SootMethod> currentStack) {
        if (currentMethod.equals(sink)) {
            allCallStacks.add(new ArrayList<>(currentStack));
            return;
        }

        Iterator<Edge> edgesOutOfCurrentMethod = cg.edgesOutOf(currentMethod);
        while (edgesOutOfCurrentMethod.hasNext()) {
            Edge edge = edgesOutOfCurrentMethod.next();
            SootMethod targetMethod = edge.tgt();
            if (!currentStack.contains(targetMethod)) { // Avoid cycles
                currentStack.push(targetMethod);
                findCallStacks(targetMethod, currentStack);
                currentStack.pop();
            }
        }
    }
}