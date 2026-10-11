package detect.knowledge.propogate;


import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * 定义了对常见数据类型的知识，如String可作为Sql注入的payload，而Long型不行
 */

@Slf4j
public class DataTypeKnow {
    ClassRefGraph classRefGraph = null;
    public HashSet<String> payloadType = new HashSet<>();

    public DataTypeKnow() {
        classRefGraph = new ClassRefGraph().buildGraph();
        stringPalLoad();
        requestPayLoad();
    }

    private void stringPalLoad() {
        // 所有能够引用String的对象类型
        List<String> allStringRefByType = classRefGraph.getAllRefByType("java.lang.String");
        List<String> allStringRefByType_filter = new LinkedList<>();
        for(String claz : allStringRefByType) {
            SootClass sootClass1 = Scene.v().getSootClassUnsafe(claz);
            if(sootClass1 != null) {
                if (sootClass1.isApplicationClass()) {
                    if (! sootClass1.getName().startsWith("java.")) {
                        allStringRefByType_filter.add(sootClass1.getName());
                    }
                }
            }
        }
        payloadType.addAll(allStringRefByType_filter);
        payloadType.add("java.lang.String");

        log.info("数据流传播Sanitizer：stringPayLoad类型数： " + payloadType.size() + " 个 " + payloadType);
    }

    private void requestPayLoad() {
        List<String> payloadClasses = new LinkedList<>();
        payloadClasses.add("javax.servlet.http.HttpServletRequest");
        payloadClasses.add("jakarta.servlet.http.HttpServletRequest");

        for(String payloadClass : payloadClasses) {
            List<String> allRefByType = classRefGraph.getAllRefByType(payloadClass);
            List<String> allRefByType_filter = new LinkedList<>();
            for(String claz : allRefByType) {
                SootClass sootClass1 = Scene.v().getSootClassUnsafe(claz);
                if(sootClass1 != null) {
                    if (sootClass1.isApplicationClass()) {
                        if (! sootClass1.getName().startsWith("java")) {
                            allRefByType_filter.add(sootClass1.getName());
                        }
                    }
                }
            }
            payloadType.addAll(allRefByType_filter);
            payloadType.add(payloadClass);
        }

    }

    private static class SingletonHolder {
        private static final DataTypeKnow INSTANCE = new DataTypeKnow();
    }

    public static DataTypeKnow getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
