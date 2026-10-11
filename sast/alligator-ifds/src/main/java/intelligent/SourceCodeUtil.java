package intelligent;

import TranModel.TranUtil;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import app.configuration.GeneralConfig;
import basic.cfg.CFG;
import basic.cfg.Node;
import basic.common.SootInit;
import basic.dataflow.DataFlow;
import basic.dataflow.Event;
import basic.util.Pair;
import basic.util.StaticAnalyzeUtils.Parameter;
import detect.unit.instrument.InstrumentUtil;
import soot.*;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class SourceCodeUtil {

    public static String srcRootDir = "";

    public static String getMethodJimpleCode(SootMethod sootMethod) {
        // 获得sourcecode,输入jar只能找到jimple表示的
        StringBuilder sourceCode = new StringBuilder();

        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
        if(methodDescriptor.cfg == null) {
            methodDescriptor.cfg = new CFG(sootMethod, true).buildCFG();
        }
        List<TransformableNode> topologicalOrderedTNodesFromCFG = TranUtil.getTopologicalOrderedTNodesFromCFG(methodDescriptor.cfg);
        for(TransformableNode tNode : topologicalOrderedTNodesFromCFG) {
            sourceCode.append(tNode.node.toString() + "\n");
        }

        return sourceCode.toString();
    }

    public static String getMethodJimpleCode_DFGBasedSlice(SootMethod sootMethod, List<Integer> csTaints) {
        // 获得sourcecode,输入jar只能找到jimple表示的
        StringBuilder sourceCode = new StringBuilder();

        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(sootMethod);
        if(methodDescriptor.cfg == null) {
            methodDescriptor.cfg = new CFG(sootMethod, true).buildCFG();
        }
        HashMap<Integer, List<Event>> parametersMapTaintedEvent = Parameter.getParametersMapTaintedEvent(methodDescriptor.cfg, sootMethod);
        HashSet<Node> sliceNodes = new HashSet<>();
        sliceNodes.addAll(Parameter.getParamTransferNodes(methodDescriptor.cfg, sootMethod));
        for(Integer paramInd : csTaints) {
            List<Event> events = parametersMapTaintedEvent.get(paramInd);
            if(events == null) continue;
            for(Event event : events) {
                sliceNodes.add(event.node);
            }
        }
        List<TransformableNode> topologicalOrderedTNodesFromCFG = TranUtil.getTopologicalOrderedTNodesFromCFG(methodDescriptor.cfg);
        for(TransformableNode tNode : topologicalOrderedTNodesFromCFG) {
            if(sliceNodes.contains(tNode.node)) {
                sourceCode.append(tNode.node.toString() + "\n");
            }
        }

        return sourceCode.toString();
    }

    public static String getMethodSourceCode(SootMethod sootMethod) {
        Pair<Integer, Integer> startAndEndLine = InstrumentUtil.getStartAndEndLine(sootMethod);
        String className = sootMethod.getDeclaringClass().getName();
        String path = className.replace('.', '/');
        // 假设根目录为项目的 src/main/java 目录
        String rootDirectory = srcRootDir;

        // 拼接路径
        String sourceCodeFilePath = rootDirectory + "/" + path + ".java";

        // 读取源代码文件的特定行范围
        int startLine = startAndEndLine.getKey(); // 起始行号
        int endLine = startAndEndLine.getValue();   // 结束行号
        String sourceCodes = readSourceCodeLines(sourceCodeFilePath, startLine, endLine);
        return sourceCodes;
    }

    public static String readSourceCodeLines(String filePath, int startLine, int endLine) {
        StringBuilder sourceCodes = new StringBuilder();
        if(!Files.exists(Paths.get(filePath))) return "";
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            int currentLine = 1;

            while ((line = reader.readLine()) != null) {
                if (currentLine >= startLine && currentLine <= endLine) {
                    if(line.startsWith("// ")) continue; // 现在为了防止暴露测试目标，不要注释行；真实场景下，建议注释行也要保留
                    sourceCodes.append(line).append("\n");
                }

                // 如果已经到达结束行号，则退出循环
                if (currentLine == endLine) {
                    break;
                }

                currentLine++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sourceCodes.toString();
    }

    public static void main(String[] args) {
        SootInit.setSoot_inputClass("/Users/fortsun/Desktop/test/赛题材料/micro_service_seclab-main/target/classes");

        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if(sootMethod.toString().contains("One")) {
                    System.out.println("----------------");
                    System.out.println(sootMethod);
                    System.out.println(getMethodSourceCode(sootMethod));
                    System.out.println("------------------");
                    System.out.println();
                }
            }
        }
    }
}
