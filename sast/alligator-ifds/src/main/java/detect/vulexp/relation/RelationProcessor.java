package detect.vulexp.relation;

import basic.cg.CG;
import detect.DetectContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.HashSet;

@Component
public class RelationProcessor {

    @Autowired
    CallRelation callRelation;

    @Autowired
    DataRelation dataRelation;

    @Autowired
    ClassFieldRelation classFieldRelation;

    public void processCall(HashSet<SootMethod> methods, CG cg) {
        callRelation.init(methods, cg);
        callRelation.printToFile();
    }

    // 会对于每个detectectContext（调用链）进行处理
    public void processData(DetectContext detectContext) {
        dataRelation.processParamTypes(detectContext); // 在这一步处理出哪些参数是链上赋值的，哪些参数来自链的源头
        dataRelation.findBranchConditions(detectContext);
        // 这是一个对于特定CVE的hack（SQL型）
//        dataRelation.findParamDef(detectContext, "java.util.Properties");
        for(String type : dataRelation.typeGeneratedInChain) {
            dataRelation.findParamDef(detectContext, type);
        }
        // 这是根据rf中的bool返回值方法找关键field，我们使用数据流中提取的condition
        for(SootMethod sootMethod : callRelation.switcherMethods) {
            dataRelation.findRelatedFieldsInMethod(detectContext, sootMethod);
        }
    }

    public void onFinishDetectContext(HashSet<SootMethod> methods, CG cg) {
        classFieldRelation.process(methods);
    }
}
