package detect.knowledge.sink;

import org.dom4j.Element;
import soot.Scene;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public abstract class HasParamMethodSinks implements MethodSinks {

    @Override
    public List<SootMethod> sinkMethods() {
        if(sinkMethods == null) {
            initSinkMethods();
        }
        return sinkMethods;
    }

    @Override
    public List<SootMethod> SinkMethodsAfterForceSync() {
        initSinkMethods();
        return sinkMethods;
    }

    @Override
    public HashMap<SootMethod, List<Integer>> sinkMethodsParams() {
        if(sinkMethodsParams == null) {
            initSinkMethods();
        }
        return sinkMethodsParams;
    }

    @Override
    public HashMap<String, Element> getConditional(){
        if(conditional == null) {
            initSinkMethods();
        }
        return conditional;
    }

    public abstract void initSinkMethods();

    protected List<SootMethod> sinkMethods = null;

    protected HashMap<SootMethod, List<Integer>> sinkMethodsParams = null;

    protected HashMap<String, Element> conditional;

    public boolean addMethodAndParam(String methodSig, int param) {
        try {
            SootMethod sootMethod = Scene.v().getMethod(methodSig);
            sinkMethods.add(sootMethod);
            sinkMethodsParams.put(sootMethod, Collections.singletonList(param));
            return true;
        } catch (Exception e){
            // 没找到这个方法
            return false;
        }
    }
}
