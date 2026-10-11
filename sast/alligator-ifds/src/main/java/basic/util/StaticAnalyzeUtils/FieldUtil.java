package basic.util.StaticAnalyzeUtils;

import basic.cfg.CFG;
import basic.cfg.Node;
import basic.cg.CG;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import soot.jimple.internal.JInstanceFieldRef;

import java.util.LinkedList;
import java.util.List;

public class FieldUtil {
    public static List<FieldRef> getParamFieldRef(SootMethod sootMethod) {
        List<FieldRef> ret = new LinkedList<>();
        List<Type> parameterTypes = new LinkedList<>(sootMethod.getParameterTypes());
        RefType thisType = sootMethod.getDeclaringClass().getType();
        parameterTypes.add(thisType);
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        for(Node node : cfg.allNodes.values()) {
            List<ValueBox> useBoxes = node.unit.getUseBoxes();
            for(ValueBox useBox : useBoxes) {
                if(useBox.getValue() instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) useBox.getValue();
                    if(fieldRef instanceof JInstanceFieldRef) {
                        JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) fieldRef;
                        Value baseValue = jInstanceFieldRef.getBase();
                        if(parameterTypes.contains(baseValue.getType())) {
                            ret.add(fieldRef);
                        }
                    }
                }
            }
        }
        return ret;
    }

    public static List<FieldRef> getParamFieldRef(SootMethod sootMethod, CFG cfg) {
        List<FieldRef> ret = new LinkedList<>();
        List<Type> parameterTypes = new LinkedList<>(sootMethod.getParameterTypes());
        RefType thisType = sootMethod.getDeclaringClass().getType();
        parameterTypes.add(thisType);
        for(Node node : cfg.allNodes.values()) {
            List<ValueBox> useBoxes = node.unit.getUseBoxes();
            for(ValueBox useBox : useBoxes) {
                if(useBox.getValue() instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) useBox.getValue();
                    if(fieldRef instanceof JInstanceFieldRef) {
                        JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) fieldRef;
                        Value baseValue = jInstanceFieldRef.getBase();
                        if(parameterTypes.contains(baseValue.getType())) {
                            ret.add(fieldRef);
                        }
                    }
                }
            }
        }
        return ret;
    }

    public static List<Node> getCallStmts(SootMethod sootMethod, CG cg) {
        List<Node> ret = new LinkedList<>();
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        for(Node node : cfg.allNodes.values()) {
            Stmt stmt = (Stmt) node.unit;
            if(cg.calleeOutOf(node.unit).size() > 0) {
                ret.add(node);
            }
        }
        return ret;
    }

    public static List<Node> getCallStmts(SootMethod sootMethod, CG cg, CFG cfg) {
        List<Node> ret = new LinkedList<>();
        for(Node node : cfg.allNodes.values()) {
            Stmt stmt = (Stmt) node.unit;
            if(cg.calleeOutOf(node.unit).size() > 0) {
                ret.add(node);
            }
        }
        return ret;
    }
}
