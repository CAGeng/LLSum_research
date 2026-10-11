package detect.knowledge.entry;


import basic.util.StaticAnalyzeUtils.MethodUtil;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * 用包含特定传参类型来决定入口
 */
@Slf4j
public class ArgTypeGuidedEntrySet extends HasParameterEntrySet{

    public HashSet<String> argTypes = new HashSet<>();

    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getClasses()){
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                int ind = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    if(argTypes.contains(type.toString())){
                        fl = 1;
                        break;
                    }
                    ind += 1;
                }
                if(fl == 1){
                    entries.add(sootMethod);
                    methodInputMap.put(sootMethod, Collections.singletonList(ind));
                }
            }
        }

        if(entries.size() > 500){
            log.error("ArgType优化前的entry数量: " + entries.size());
            entries = filterOverrideEntries(entries);
            log.error("优化后（去冗余）且有重写的entry数量: " + entries.size());
        }
    }

    public static List<SootMethod> filterOverrideEntries(List<SootMethod> entries) {
        HashSet<SootMethod> overrideOtimizedEntries = new HashSet<>();

        for(SootMethod sootMethod : entries) {
            SootMethod overrideAncestor = MethodUtil.findOverrideAncestor(sootMethod);
            if(!overrideAncestor.equals(sootMethod)) {
                log.error("entry方法 " + sootMethod.getSignature() + " 继承自 " + overrideAncestor.getSignature());
                overrideOtimizedEntries.add(sootMethod);
            }
        }

        return new LinkedList<>(overrideOtimizedEntries);
    }
}
