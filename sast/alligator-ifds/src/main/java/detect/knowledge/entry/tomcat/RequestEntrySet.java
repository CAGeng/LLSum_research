package detect.knowledge.entry.tomcat;

import basic.util.StaticAnalyzeUtils.MethodUtil;
import detect.knowledge.entry.EntrySet;
import lombok.extern.slf4j.Slf4j;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static detect.knowledge.entry.ArgTypeGuidedEntrySet.filterOverrideEntries;

@Slf4j
public class RequestEntrySet extends EntrySet {
    @Override
    public void detectEntries() {
        for(SootClass sootClass : Scene.v().getClasses()){
            for(SootMethod sootMethod : sootClass.getMethods()){
                if(sootMethod.getName().equals("<init>") || sootMethod.getName().equals("<clinit>")) continue;
                int fl = 0;
                for(Type type : sootMethod.getParameterTypes()){
                    String[] split = type.toString().split("\\.");
                    String typeName = split[split.length - 1];
                    if(typeName.toLowerCase().contains("request")){
                        fl = 1;
                    }
                    if(typeName.toLowerCase().contains("event")){
                        fl = 1;
                    }
                    if(typeName.toLowerCase().endsWith("exchange")){
                        fl = 1;
                    }
                    if(typeName.toLowerCase().endsWith("callback")){
                        fl = 1;
                    }
                    if(typeName.toLowerCase().endsWith("message")){
                        fl = 1;
                    }
                }
                if(fl == 1){
                    entries.add(sootMethod);
                }
            }
        }

        if(entries.size() > 100) {
            log.error("RequestEntrySet优化前的entry数量: " + entries.size());
            entries = filterOverrideEntries(entries);
            log.error("优化后（去冗余）且有重写的entry数量: " + entries.size());
        }

    }
}
