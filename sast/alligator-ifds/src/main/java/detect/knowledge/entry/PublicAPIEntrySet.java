package detect.knowledge.entry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;


@Slf4j
public class PublicAPIEntrySet extends EntrySet{

    @Override
    public void detectEntries() {
        log.info("Detecting public API entry points");
        for(SootClass sootClass : Scene.v().getClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if(sootMethod.isPublic()) {
                    entries.add(sootMethod);
                }
            }
        }
    }
}
