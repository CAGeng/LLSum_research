package TranModel.Rules;

import TranModel.Rule;
import TranModel.Transformable;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.util.StaticAnalyzeUtils.Parameter;
import lombok.extern.slf4j.Slf4j;
import soot.Value;
import soot.jimple.IfStmt;
import soot.jimple.Stmt;

@Slf4j
public class ImmediateSpreadRule implements Rule {

    // todo
    @Override
    public void apply(Transformable transformable) {
        TransformableNode transformableNode = (TransformableNode) transformable;
        IfStmt ifStmt = Parameter.getIfStmt(transformableNode);
        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(transformableNode.method);

        if (ifStmt == null)
            return;

        Stmt target = ifStmt.getTarget();
        Value condition = ifStmt.getCondition();
        log.error("target: " + target.toString());
        log.error("condition: " + condition.toString());

        for (TransformableNode success: transformableNode.successors){
            if (((Stmt)success.node.unit).equals(target) ){
                log.error("success: " + success.node.unit.toString());
            }else {
                log.error("success(false): " + success.node.unit.toString());
            }
        }
    }

    @Override
    public String getName() {
        return "ImmediateSpreadRule";
    }
}
