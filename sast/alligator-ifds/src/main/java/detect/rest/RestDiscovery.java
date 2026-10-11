package detect.rest;

import basic.cfg.CFG;
import basic.cfg.Node;
import basic.common.SootInit;
import basic.dataflow.DataFlow;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import soot.*;
import soot.jimple.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;

public class RestDiscovery {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        new RestDiscovery().run();
    }

    public void run() {
        SootInit.setSoot_inputClass("/Users/fortsun/Desktop/test/es-8.8.2-soot");
        SootClass restHandlerBaseClass = Scene.v().getSootClass("org.elasticsearch.rest.RestHandler");
        HashSet<SootClass> implementClasses = ClassUtils.getAllSubs(restHandlerBaseClass);
        System.out.println("继承RestHandler的类数量: " + implementClasses.size());
        ObjectNode root = objectMapper.createObjectNode();
        for(SootClass implement : implementClasses) {
            System.out.println(implement);
            SootMethod routesMethod = implement.getMethodByNameUnsafe("routes");
            if(routesMethod != null) {
                System.out.println("    " + routesMethod);
                ArrayNode routesNode = processRoutes(routesMethod);
                root.set(implement.getName(), routesNode);
            }
        }
        String filePath = "output.json";

        // 将 ObjectNode 写入文件
        try (FileWriter fileWriter = new FileWriter(new File(filePath))) {
            objectMapper.writeValue(fileWriter, root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // 返回记录这个routes方法中所有route的json ArrayNode
    public ArrayNode processRoutes(SootMethod routesMethod) {

        ArrayNode routesArrayNode = objectMapper.createArrayNode();

        CFG cfg = new CFG(routesMethod, true);
        cfg.buildCFG();
        for(Node cfgNode : cfg.allNodes.values()) {
            Stmt stmt = (Stmt) cfgNode.unit;
            if (stmt.containsInvokeExpr()) {
                InvokeExpr invokeExpr = stmt.getInvokeExpr();
                if (invokeExpr instanceof SpecialInvokeExpr) {
                    SpecialInvokeExpr specialInvokeExpr = (SpecialInvokeExpr) invokeExpr;
                    SootMethod invokeMethod = specialInvokeExpr.getMethod();

                    // 判断是否是构造函数，并且构造函数的声明类是特定的类
                    if (invokeMethod.getName().equals("<init>") &&
                            invokeMethod.getDeclaringClass().getName().equals("org.elasticsearch.rest.RestHandler$Route")) {

                        // 找到一条route
                        ObjectNode routeNode = objectMapper.createObjectNode();
                        String url = "unknown";
                        String verb = "unknown";

                        Value argValueUrl = specialInvokeExpr.getArg(1);
                        System.out.println("Argument value URL: " + argValueUrl);
                        url = argValueUrl.toString().replace("\"", "");

                        ValueBox argBoxVerb = specialInvokeExpr.getArgBox(0);
                        HashSet<Node> allDefUnitAffectThisValue = DataFlow.findAllDefUnitAffectThisValue(cfgNode, argBoxVerb);
                        for(Node node : allDefUnitAffectThisValue) {
                            Unit unit = node.unit;
                            if (unit instanceof AssignStmt) {
                                AssignStmt assignStmt = (AssignStmt) unit;
                                Value leftValue = assignStmt.getLeftOp();
                                Value rightValue = assignStmt.getRightOp();

                                if (leftValue instanceof Local && rightValue instanceof FieldRef) {
                                    FieldRef fieldRef = (FieldRef) rightValue;
                                    if (fieldRef.getField().getType().toString().equals("org.elasticsearch.rest.RestRequest$Method")) {
                                        verb = fieldRef.getField().getName();
                                        System.out.println("Argument value Verb: " + verb);
                                    }
                                }
                            }
                        }

                        routeNode.put("Verb", verb);
                        routeNode.put("URL", url);
                        routesArrayNode.add(routeNode);
                    }

                }
            }
        }
        return routesArrayNode;
    }
}
