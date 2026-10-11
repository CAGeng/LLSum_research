package detect.knowledge.entry.spring;

import detect.knowledge.entry.EntrySet;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class ServiceEntrySet extends EntrySet {

    @Override
    public void detectEntries() {
        for (SootClass claz : Scene.v().getClasses()) {
            if (claz.getName().endsWith("ServiceImpl")) {
                entries.addAll(claz.getMethods());
            }
        }
    }
}
