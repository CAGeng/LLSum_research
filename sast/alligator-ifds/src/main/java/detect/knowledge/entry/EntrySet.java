package detect.knowledge.entry;

import lombok.extern.slf4j.Slf4j;
import soot.SootMethod;

import java.util.LinkedList;
import java.util.List;

@Slf4j
public abstract class EntrySet {

    public List<SootMethod> entries;

    public List<SootMethod> getEntries(){
        if(entries == null){
            entries = new LinkedList<>();
            detectEntries();
        }
        return entries;
    }

    public List<SootMethod> forceRefreshEntries(){
        entries = new LinkedList<>();
        // try-catch
        try {
            detectEntries();
        } catch (Exception e) {
            log.error("Error in detectEntries : " + this.getClass());
        }


        return entries;
    }

    public abstract void detectEntries();
}
