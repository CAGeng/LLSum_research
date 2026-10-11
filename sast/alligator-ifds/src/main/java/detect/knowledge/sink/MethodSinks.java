package detect.knowledge.sink;

import org.dom4j.Element;
import soot.SootMethod;

import java.util.HashMap;
import java.util.List;

public interface MethodSinks {
    List<SootMethod> sinkMethods();

    List<SootMethod> SinkMethodsAfterForceSync();
    HashMap<SootMethod, List<Integer>> sinkMethodsParams();
    String getTypeName();
    HashMap<String, Element> getConditional();
}
