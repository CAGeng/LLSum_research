package ai;

import TranModel.TranUtil;
import TranModel.TransformableNode;
import basic.cfg.CFG;
import basic.common.SootInit;
import basic.util.Pair;
import detect.unit.instrument.InstrumentUtil;
import org.junit.jupiter.api.Test;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.List;

public class SourceCodeMapTest {

    @Test
    public void testSourceCode() {
        SootInit.setSoot_inputClass("/Users/fortsun/Desktop/test/赛题材料/micro_service_seclab-main/target/classes");

        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if(sootMethod.toString().contains("One")) {
                    System.out.println(sootMethod);
                    Pair<Integer, Integer> startAndEndLine = InstrumentUtil.getStartAndEndLine(sootMethod);
                    System.out.println(startAndEndLine.getKey() + " " + startAndEndLine.getValue());
                    System.out.println(sootMethod.retrieveActiveBody().toString());
                }
            }
        }
    }
}
