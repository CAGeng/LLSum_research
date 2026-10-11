package detect.vulexp.relation;

import app.configuration.GeneralConfig;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.dataflow.DataFlow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.internal.JInstanceFieldRef;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

@Component
@Slf4j
public class ClassFieldRelation {

    public HashSet<SootMethod> methods = new HashSet<>(); // 传入的rf
    public HashMap<String, String> field2Default = new HashMap<>();
    public HashSet<SootField> staticFields = new HashSet<>();
    @Autowired
    GeneralConfig generalConfig;

    @Autowired CallRelation callRelation;
    @Autowired DataRelation dataRelation;

    public Writer writerFields, writerAllClasses, writerAllFields;

    public HashSet<SootField> allConstraintsFieldsCandidates = new HashSet<>();
    public Writer writerAllConstraintsFields, writerFieldDefApis;
    public HashSet<SootMethod> fieldDefAPIs = new HashSet<>();

    @PostConstruct
    private void init() {
        try {
            Path outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("patch_related_fields.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerFields = new BufferedWriter(new FileWriter(outputPath.toString()));
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("allConstraintsFieldsCandidates.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerAllConstraintsFields = new BufferedWriter(new FileWriter(outputPath.toString()));
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("fieldDefAPIs.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerFieldDefApis = new BufferedWriter(new FileWriter(outputPath.toString()));
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("all/fields.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerAllFields = new BufferedWriter(new FileWriter(outputPath.toString()));
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("all/classes.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerAllClasses = new BufferedWriter(new FileWriter(outputPath.toString()));
        } catch (IOException e) {
            log.error("Failed to initialize writerFields", e);
            throw new RuntimeException("Failed to initialize ClassFieldRelation", e); // 更严格的错误处理
        }
    }

    @PreDestroy
    public void closeWriter() {
        try {
            if (writerFields != null) {
                writerFields.close();
            }
            if(writerAllConstraintsFields != null) {
                writerAllConstraintsFields.close();
            }
            if(writerFieldDefApis != null) {
                writerFieldDefApis.close();
            }
            if(writerAllFields != null) {
                writerAllFields.close();
            }
            if(writerAllClasses != null) {
                writerAllClasses.close();
            }
        } catch (IOException e) {
            log.error("Failed to close writers", e);
        }
    }

    public void process(HashSet<SootMethod> sinkMethods) {

        recordAll();

        this.methods = new HashSet<>(sinkMethods);

        HashSet<SootClass> sinkClasses = new HashSet<>();
        for(SootMethod method : methods) {
            sinkClasses.add(method.getDeclaringClass());
        }

        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            if(!sinkClasses.contains(sootClass)) continue;
            log.info("正在处理类: {}", sootClass.getName());
            for(SootField field : sootClass.getFields()) {
                if(field.isStatic()) {
                    String declaration = field.getDeclaration();
                    log.info("找到静态变量: {}", declaration);
                    try {
                        writerFields.write("sinkclass中的static field: " + field.getSignature() + "\n");
                        staticFields.add(field);
                        allConstraintsFieldsCandidates.add(field);
                    } catch (IOException e) {
                    }
                }
                if(field.getName().startsWith("allow")) {
                    try {
                        writerFields.write("sinkclass中包含特定关键词的field: " + field.getSignature() + "\n");
                        allConstraintsFieldsCandidates.add(field);
                    } catch (IOException e) {
                    }
                }
            }

            // 找field定义，暂时没用上
            SootMethod clinitMethod = sootClass.getMethodByNameUnsafe("<clinit>");
            if(clinitMethod != null) {
                CFG cfg = new CFG(clinitMethod, true).buildCFG();
                for(Node node : cfg.allNodes.values()) {
                    for (ValueBox valueBox : node.unit.getDefBoxes()) {
                        if(valueBox.getValue() instanceof StaticFieldRef) {
                            SootField field = ((FieldRef) valueBox.getValue()).getField();
                            String defStr = "";
                            for(ValueBox useBox : node.unit.getUseBoxes()) {
                                defStr += useBox.getValue();
                            }
                            field2Default.put(field.getSignature(), defStr);
                            log.info("找到field定义: " + field.getSignature() + " 定义: " + defStr);
                        }
                    }
                }
            }
        }

        HashSet<SootClass> switcherClasses = new HashSet<>();
        for(SootMethod method : callRelation.switcherMethods) {
            switcherClasses.add(method.getDeclaringClass());
        }
        for(SootClass sootClass : switcherClasses) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                boolean containsConstraintStr = false; // 看这个方法是否包含constraint常量字符串
                if(!sootMethod.hasActiveBody()) continue;
                for(Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
                    for(String csstr : dataRelation.constraintString) {
                        if(unit.toString().contains(csstr)) {
                            log.info("找到约束字符串: " + csstr);
                            containsConstraintStr = true;
                            break;
                        }
                    }
                }
                if(containsConstraintStr) {
                    log.info("使用约束string的方法: " + sootMethod);
                    getAllIfs(sootMethod);
                }
            }
        }

        // 把data param那种形式提取到的也拿过来
        allConstraintsFieldsCandidates.addAll(dataRelation.allConstraintFields);

        // 把所有rf中的if中用到的field提出来
        for(SootMethod sootMethod : methods) {
            getAllIfs(sootMethod);
        }

        for(SootField sootField : allConstraintsFieldsCandidates) {
            try {
                writerAllConstraintsFields.write(sootField.getSignature() + "\n");
                findInstanceFieldDef(sootField);
            } catch (IOException e) {
            }
        }

    }

    public List<Node> getAllIfs(SootMethod sootMethod) {
        log.info("获得" + sootMethod.getSignature() + "中的所有if变量...");
        List<Node> ret = new LinkedList<>();
        // 找用于if的变量
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        for(Node node : cfg.allNodes.values()) {
            Stmt stmt = (Stmt) node.unit;
            if(stmt.toString().startsWith("if ")) {
                for(ValueBox valueBox : stmt.getUseBoxes()) {
//                    log.info("if条件变量: " + valueBox.getValue());
                    List<Node> defNodes = DataFlow.getDefNodes(valueBox, node);
                    for(Node defNode : defNodes) {
                        log.info("if条件变量定义: " + defNode.unit);
                        Stmt defStmt = (Stmt) defNode.unit;
                        if(defStmt instanceof AssignStmt) {
                            AssignStmt assignStmt = (AssignStmt) defStmt;
                            Value rightOp = assignStmt.getRightOp();
                            if(rightOp instanceof JInstanceFieldRef) {
                                JInstanceFieldRef jInstanceFieldRef = (JInstanceFieldRef) rightOp;
                                log.info("if条件变量定义包含field: " + jInstanceFieldRef.getField().getSignature());
                                try {
                                    writerFields.write("if条件变量定义包含field: " + jInstanceFieldRef.getField().getSignature() + "\n");
                                    allConstraintsFieldsCandidates.add(jInstanceFieldRef.getField());
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else if(defStmt.containsInvokeExpr()) {
                                SootMethod method = defStmt.getInvokeExpr().getMethod();
                                if(!method.getDeclaringClass().getName().startsWith("java")) {
                                    HashSet<SootField> sootFields = allFieldUsedInMethod(method);
                                    log.info("在方法" + method + "中...");
                                    for(SootField sootField : sootFields) {
                                        log.info("if条件变量定义包含field: " + sootField.getSignature());
                                        try {
                                            writerFields.write("if条件变量定义包含field: " + sootField.getSignature() + "\n");
                                            allConstraintsFieldsCandidates.add(sootField);
                                        } catch (IOException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    public HashSet<SootField> allFieldUsedInMethod(SootMethod sootMethod) {
        HashSet<SootField> ret = new HashSet<>();
        if(sootMethod.isPhantom()) return ret;
        for(Unit unit : sootMethod.retrieveActiveBody().getUnits()) {
            for(ValueBox valueBox : unit.getUseBoxes()) {
                if(valueBox.getValue() instanceof FieldRef) {
                    FieldRef fieldRef = (FieldRef) valueBox.getValue();
                    SootField field = fieldRef.getField();
                    log.info("找到方法中field使用: " + field.getSignature());
                    ret.add(field);
                }
            }
        }
        return ret;
    }

    private void findInstanceFieldDef(SootField field) {
        SootClass declaringClass = field.getDeclaringClass();
        for(SootMethod sootMethod : declaringClass.getMethods()) {
            CFG cfg = new CFG(sootMethod, true).buildCFG();
            for(Node node : cfg.allNodes.values()) {
                for(ValueBox valueBox : node.unit.getDefBoxes()) {
                    Value value = valueBox.getValue();
                    if(value instanceof JInstanceFieldRef) {
                        JInstanceFieldRef instanceFieldRef = (JInstanceFieldRef) value;

                        if(instanceFieldRef.getField().getName().equals(field.getName())) {
                            log.warn("找到field的赋值点: " + node.unit);
                            try {
                                fieldDefAPIs.add(sootMethod);
                                writerFieldDefApis.write(sootMethod.getSignature() + "\n");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    public void recordAll() {
        try {
            for(SootClass sootClass : Scene.v().getApplicationClasses()) {
                writerAllClasses.write(sootClass.getName() + "\n");
                for(SootField sootField : sootClass.getFields()) {
                    writerAllFields.write(sootField.getSignature() + "\n");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
