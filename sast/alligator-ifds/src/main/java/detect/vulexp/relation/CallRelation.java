package detect.vulexp.relation;

import app.configuration.GeneralConfig;
import basic.cg.CG;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.MethodOrMethodContext;
import soot.SootMethod;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

@Component
public class CallRelation {

    public HashSet<SootMethod> methods = new HashSet<>();
    public HashMap<SootMethod, HashSet<SootMethod>> method2calleeEdges = new HashMap<>();

    public HashSet<SootMethod> switcherMethods = new HashSet<>();

    @Autowired
    GeneralConfig generalConfig;

    public void init(HashSet<SootMethod> methods, CG cg) {
        this.methods = new HashSet<>(methods);
        for(SootMethod method : methods) {
            Iterator<MethodOrMethodContext> allMethodsCalledBy = cg.getAllMethodsCalledBy(method);
            while(allMethodsCalledBy.hasNext()) {
                MethodOrMethodContext next = allMethodsCalledBy.next();
                if(methods.contains(next.method())) {
                    if(!method2calleeEdges.containsKey(method)) {
                        method2calleeEdges.put(method, new HashSet<>());
                    }
                    method2calleeEdges.get(method).add(next.method());
                }
            }
        }

        switcherFind();
    }

    // 对于返回值为bool的risky func，先默认都为开关
    public void switcherFind(){
        for(SootMethod method : methods) {
            if(method.getReturnType().toString().contains("boolean")) {
                switcherMethods.add(method);
            }
        }
    }

    public void printToFile() {
        Path outputPath = Paths.get(generalConfig.sootOutputPath).resolve("relations/call-relations");
        try {
            // 确保目标目录存在，如果不存在，则创建
            Files.createDirectories(outputPath.getParent());

            // 使用 BufferedWriter 写入文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toString()))) {
                for(SootMethod method : methods) {
                    writer.write(method.toString());
                    writer.newLine();
                    if(method2calleeEdges.containsKey(method)) {
                        for(SootMethod callee : method2calleeEdges.get(method)) {
                            writer.write("\t" + callee);
                            writer.newLine();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
