package detect.dataflow;

import TranModel.datastruct.TaintNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.reflect.Bridge;
import basic.reflect.LoadClass;
import detect.knowledge.propogate.DataTypeKnow;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class TaintSanitizer {
    public void sanitizeStringPayload(SootMethod sootMethod){

        Boolean[] isTaint = new Boolean[sootMethod.getParameterCount()];

        Method method = Bridge.tryGetMethod(sootMethod.getDeclaringClass().getName(), sootMethod.getSubSignature(),
                LoadClass.getInstance().urlClassLoader);
        if (method != null) {
            Type[] genericParameterTypes = method.getGenericParameterTypes();
            for (int ind = 0; ind < genericParameterTypes.length; ind++) {
                Type paramType = genericParameterTypes[ind];
                isTaint[ind] = DataTypeKnow.getInstance().payloadType.contains(paramType.getTypeName());
                if (paramType instanceof ParameterizedType) {
                    // 处理范型，特别是list/map等类型
                    ParameterizedType parameterizedType = (ParameterizedType) paramType;
                    Type[] typeArguments = parameterizedType.getActualTypeArguments();
                    // typeArguments 包含了泛型的类型信息
                    for(Type type : typeArguments) {
                        if(DataTypeKnow.getInstance().payloadType.contains(type.getTypeName())) {
                            isTaint[ind] = true;
                        }
                    }
                }
            }
        } else {
            // 用soot的方法获取参数类型
            for (int ind = 0; ind < sootMethod.getParameterCount(); ind++) {
                soot.Type type = sootMethod.getParameterTypes().get(ind);
                isTaint[ind] = DataTypeKnow.getInstance().payloadType.contains(type.toString());
            }
        }

        // 只保留能作为StringPayload类型的参数作为污点
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
        List<TaintNode> willBeDeleted = new LinkedList<>();
        for(TaintNode taintNode : methodDescriptor.allTaintNodes) {
            if(taintNode.paramInd != null) {
                if(taintNode.paramInd == -1){
                    // 不做处理
                }else if(!isTaint[taintNode.paramInd]) {
                    willBeDeleted.add(taintNode);
                }
            }
        }
        if(willBeDeleted.size() > 0) {
            log.warn("方法 " + sootMethod + " 的污点参数数目从 " + methodDescriptor.allTaintNodes.size() + " 个减少到 "
                    + (methodDescriptor.allTaintNodes.size() - willBeDeleted.size()) + " 个");
        }
        for(TaintNode taintNode : willBeDeleted) {
            methodDescriptor.deleteTaintNode(taintNode);
        }
    }

    public void test() {
        SootClass sootClass = Scene.v().getSootClassUnsafe("com.l4yn3.microserviceseclab.data.Student2");
        for(SootMethod sootMethod : sootClass.getMethods()) {
            if(sootMethod.getName().equals("add")) {
                System.out.println(sootMethod.getSignature());
                sanitizeStringPayload(sootMethod);
            }
        }
    }
}
