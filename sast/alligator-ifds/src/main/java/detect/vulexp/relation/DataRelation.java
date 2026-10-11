package detect.vulexp.relation;

import TranModel.TransformableNode;
import TranModel.datastruct.ConditionNode;
import app.configuration.GeneralConfig;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.dataflow.DataFlow;
import basic.util.LogUtil;
import detect.DetectContext;
import detect.dataflow.DataFlowTrack;
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
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

/**
 * 这个类提取链上传播的数据类型，寻找它们和利用信息中约束的关系
 * 提取所有分支条件，并和利用信息关联
 */
@Component
@Slf4j
public class DataRelation {

    @Autowired
    GeneralConfig generalConfig;

    public Writer writerAssigns, writer_fieldAssign_APIs;
    public Writer typeGeneratedInChainWriter; // 在链上产生的参数类型
    public HashSet<String> typeGeneratedInChain = new HashSet<>(); // 在链上产生的参数类型
    private HashSet<SootMethod> fieldAssignApis = new HashSet<>();

    public HashSet<String> constraintString = new HashSet<>(); // 和约束有关的字符串
    public HashSet<SootField> allConstraintFields = new HashSet<>();

    @PostConstruct // 依赖注入完成后执行初始化逻辑
    private void init() {
        Path outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("data-assigns.txt");
        try {
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writerAssigns = new BufferedWriter(new FileWriter(outputPath.toString()));
        } catch (IOException e) {
            log.error("Failed to initialize writerAssigns", e);
            throw new RuntimeException("Failed to initialize DataRelation", e); // 更严格的错误处理
        }
        try {
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("field_assign_apis.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            writer_fieldAssign_APIs = new BufferedWriter(new FileWriter(outputPath.toString()));
            outputPath = Paths.get(generalConfig.getSootOutputPath()).resolve("typeGeneratedInChain.txt");
            Files.createDirectories(outputPath.getParent()); // 确保父目录存在
            typeGeneratedInChainWriter = new BufferedWriter(new FileWriter(outputPath.toString()));
        } catch (IOException e) {
            log.error("Failed to initialize writerAssigns", e);
            throw new RuntimeException("Failed to initialize DataRelation", e); // 更严格的错误处理
        }
    }

    @PreDestroy
    public void closeWriter() {
        try {
            if (writerAssigns != null) {
                writerAssigns.close();
            }
            if (writer_fieldAssign_APIs != null) {
                writer_fieldAssign_APIs.close();
            }
            if (typeGeneratedInChainWriter != null) {
                typeGeneratedInChainWriter.close();
            }
        } catch (IOException e) {
            log.error("Failed to close writerAssigns", e);
        }
    }

    private List<String> ignoredTypeNames = Arrays.asList("java.lang.String", "java.lang.Integer", "java.lang.Boolean", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Object", "java.lang.Class", "java.lang.Enum", "java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException", "java.lang.Error", "java.lang.StackTraceElement");
    private List<String> configTypeNames = Arrays.asList("properties", "config");

    public void processParamTypes(DetectContext detectContext) {
        HashSet<String> paramTypes = new HashSet<>();
        List<SootMethod> callStack = detectContext.callStack;
        List<Type> callerTypes = null;
        for(SootMethod sootMethod : callStack) {
            for(Type type : sootMethod.getParameterTypes()) {
                paramTypes.add(type.toString());
                if(callerTypes != null && !callerTypes.contains(type) && !ignoredTypeNames.contains(type.toString())){
                    log.info("找到一个新的参数类型: " + type.toString());
                    try {
                        typeGeneratedInChainWriter.write("链上产生的参数类型: " + type.toString() + "\n");
                        for(String configTypeName : configTypeNames) {
                            if(type.toString().toLowerCase().contains(configTypeName)) {
                                log.info("找到一个配置类型: " + type.toString());
                                typeGeneratedInChainWriter.write("配置参数类型: " + type.toString() + "\n");
                                typeGeneratedInChain.add(type.toString());
                            }
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            callerTypes = sootMethod.getParameterTypes();
        }
        Path outputPath = Paths.get(generalConfig.sootOutputPath).resolve("relations").resolve(String.valueOf(detectContext.id)).resolve("param-types.txt");
        try {
            // 确保目标目录存在，如果不存在，则创建
            Files.createDirectories(outputPath.getParent());

            // 使用 BufferedWriter 写入文件
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toString()))) {
                for(String type : paramTypes) {
                    writer.write(type + "\n");
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void findBranchConditions(DetectContext detectContext) {

        Path outputPath = Paths.get(generalConfig.sootOutputPath).resolve("relations").resolve(String.valueOf(detectContext.id));
        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        DataFlowTrack.DataFlowResult dataFlowResult = detectContext.dataFlowResult;
        LinkedList<ConditionNode> conditionNodes = dataFlowResult.conditionNodes;
        LogUtil.outputToJSON(conditionNodes, outputPath.toString(), "conditions.json");
    }

    /**
     * 寻找一个重要参数的调用位置，如果在A->B(param)的调用中，param定义应出现在A中，尝试寻找A的定义和谁有关，尤其是哪些field
     */
    public void findParamDef(DetectContext detectContext, String typeStr) {
        List<SootMethod> callStack = detectContext.callStack;
        List<SootMethod> notUseThisParamMethods = new LinkedList<>();
        for(SootMethod sootMethod : callStack) {
            boolean contains = false;
            for(Type type : sootMethod.getParameterTypes()) {
                if(type.toString().equals(typeStr)) contains = true;
            }
            if(contains) {
                log.info("这是调用栈上第一个使用这个类型参数的方法: " + sootMethod);
                break;
            } else {
                notUseThisParamMethods.add(sootMethod);
            }
        }
        if(notUseThisParamMethods.isEmpty()) {
            log.info("这个来自最上层参数，无法确定赋值情况");
            return;
        }
        log.info("寻找这个变量的赋值");
        SootMethod method = notUseThisParamMethods.get(notUseThisParamMethods.size() - 1);
        CFG cfg = new CFG(method, true).buildCFG();
        for(Node node : cfg.allNodes.values()) {
            for(ValueBox valueBox : node.unit.getDefBoxes()) {
                if(valueBox.getValue().getType().toString().equals(typeStr)) {
                    log.info("找到了赋值点: " + node.unit);
                    try {
                        writerAssigns.write("对于类型: " + typeStr + "\n");
                        writerAssigns.write("Method: " + method + "\n");
                        writerAssigns.write("Assign: " + node.unit + "\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Stmt stmt = (Stmt) node.unit;
                    if(stmt instanceof AssignStmt) {
                        assignUnit(node);
                    }

                }
            }
        }

        try {
            writer_fieldAssign_APIs.write("------ 对于类型为 " + typeStr + " 的参数 ------" + "\n");
            for(SootMethod assignApi : fieldAssignApis) {
                writer_fieldAssign_APIs.write(assignApi.getSignature() + "\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 寻找一个switcher方法中相关的field, 把detectContext也传进来，因为其中的condition信息能简化这个操作
     * 对于patch所在的switcher方法，会特别关注
     */
    private HashSet<SootMethod> visitedSwitcherMethods = new HashSet<>(); // 不重复看
    public void findRelatedFieldsInMethod(DetectContext detectContext, SootMethod sootMethod) {
        if(visitedSwitcherMethods.contains(sootMethod) || !detectContext.callStack.contains(sootMethod)) return;
        visitedSwitcherMethods.add(sootMethod);


        // 用condition找一下
        // todo: 这个方案目前没有找到适用的案例
        LinkedList<ConditionNode> conditionNodes = detectContext.dataFlowResult.conditionNodes;
//        System.out.println(conditionNodes.size());
        for(ConditionNode conditionNode : conditionNodes) {
            TransformableNode tfNode = conditionNode.tfNode;
            if(tfNode.containsInvoke()) {
                for(SootMethod method : tfNode.invokeMethods()) {
                    if(method.equals(sootMethod)) {
                        log.info("在condition上发现invoke switcher: " + method);
                    }
                }
            }
            if(conditionNode.methodSig.equals(sootMethod.getSignature())) {
                for(ValueBox valueBox : tfNode.node.unit.getUseBoxes()) {
                    if(valueBox instanceof JInstanceFieldRef) {
                        SootField field = ((FieldRef) valueBox.getValue()).getField();
                        log.info("在switcher method的condition上发现instance field: " + field);
                    } else if(valueBox instanceof StaticFieldRef) {
                        SootField field = ((FieldRef) valueBox.getValue()).getField();
                        log.info("在switcher method的condition上发现static field: " + field);
                    }
                }
            }

            if(conditionNode.constants.containsKey("java.lang.String")) {
                if(conditionNode.constants.get("java.lang.String").startsWith("\"")){
                    log.info("在condition上发现String: " + conditionNode.constants.get("java.lang.String"));
                    constraintString.add(conditionNode.constants.get("java.lang.String"));
                }
            }
        }

        // 在switcher中找和ret相关的field引用
    }


    private void assignUnit(Node node) {
        Stmt stmt = (Stmt) node.unit;
        if(stmt instanceof AssignStmt) {
            for(ValueBox valueBox : stmt.getUseBoxes()) {
                Value value = valueBox.getValue();
                log.debug("assign unit: " + node);
                log.info("使用了: " + value);
                if(value instanceof JInstanceFieldRef) {
                    log.info("发现instance field : " + value);
                    try {
                        writerAssigns.write("Instance Field: " + value + "\n");
                        SootField field = ((FieldRef) value).getField();
                        findInstanceFieldDef(field);
                        allConstraintFields.add(field);

                        log.info("寻找其他类似的field...");
                        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
                            for(SootField sootField : sootClass.getFields()) {
                                if(sootField.getName().equals(field.getName())) {
                                    log.info("发现类似的field: " + sootField);
                                    findInstanceFieldDef(sootField);
                                    allConstraintFields.add(sootField);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }else if(value instanceof StaticFieldRef) {
                    log.info("发现static field : " + value);
                    try {
                        writerAssigns.write("Static Field: " + value + "\n");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if(stmt.containsInvokeExpr()) {
                    SootMethod method = stmt.getInvokeExpr().getMethod();
                    log.info("发现调用: " + method);
                    findRetAssign(method);
                }
            }
        }
    }

    private void findInstanceFieldDef(SootField field) {
        SootClass declaringClass = field.getDeclaringClass();
        for(SootMethod sootMethod : declaringClass.getMethods()) {
            log.info("处理方法: " + sootMethod);
            CFG cfg = new CFG(sootMethod, true).buildCFG();
            for(Node node : cfg.allNodes.values()) {
                for(ValueBox valueBox : node.unit.getDefBoxes()) {
                    Value value = valueBox.getValue();
                    if(value instanceof JInstanceFieldRef) {
                        JInstanceFieldRef instanceFieldRef = (JInstanceFieldRef) value;

                        if(instanceFieldRef.getField().getName().equals(field.getName())) {
                            log.warn("找到field的其他赋值点: " + node.unit);
                            try {
                                writerAssigns.write("对于instance field: " + field + "\n");
                                writerAssigns.write("Method: " + sootMethod + "\n");
                                writerAssigns.write("Assign: " + node.unit + "\n");

                                fieldAssignApis.add(sootMethod);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

    private void findRetAssign(SootMethod sootMethod) {
        if(sootMethod.getReturnType().toString().equals("void")) {
            log.debug("void 方法不需要寻找返回值赋值点");
            return;
        }
        CFG cfg = new CFG(sootMethod, true).buildCFG();
        for(Node node : cfg.allNodes.values()) {
            if(node.unit.toString().startsWith("return ")) {
                for(ValueBox valueBox : node.unit.getUseBoxes()) {
                    for(Node defNode : DataFlow.getDefNodes(valueBox, node)) {
                        log.info("找到了赋值点: " + defNode.unit);
                        try {
                            writerAssigns.write("对于返回值: " + sootMethod.getReturnType() + "\n");
                            writerAssigns.write("Method: " + sootMethod + "\n");
                            writerAssigns.write("Assign: " + defNode.unit + "\n");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        assignUnit(defNode);
                    }
                }
            }
        }
    }
}
