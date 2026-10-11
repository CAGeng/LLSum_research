package detect.knowledge.entry;

import soot.SootMethod;

import java.util.HashMap;
import java.util.List;

public abstract class HasParameterEntrySet extends EntrySet{
    public HashMap<SootMethod, List<Integer>> methodInputMap = new HashMap<>();
}
