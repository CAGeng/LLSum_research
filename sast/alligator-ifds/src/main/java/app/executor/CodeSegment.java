package app.executor;

import lombok.Data;
import lombok.NoArgsConstructor;
import soot.SootMethod;

import java.util.List;

/**
 * User: luca
 * Date: 2024/7/9
 * Description:
 */


public class CodeSegment {

    private List<SootMethod> segSootMethods;

    public CodeSegment(List<SootMethod> segSootMethods) {
        this.segSootMethods = segSootMethods;
    }

    public List<SootMethod> getSegSootMethods() {
        return segSootMethods;
    }

    public void setSegSootMethods(List<SootMethod> segSootMethods) {
        this.segSootMethods = segSootMethods;
    }

}
