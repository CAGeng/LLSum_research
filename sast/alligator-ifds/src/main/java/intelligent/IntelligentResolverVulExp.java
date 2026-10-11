package intelligent;

import TranModel.TranUtil;
import TranModel.TransformableNode;
import basic.DefaultDetector.MethodDescriptor;
import basic.cfg.CFG;
import detect.DetectContext;
import detect.ExploitDetector;
import detect.vulexp.datastruct.BasicInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.List;
import java.util.Scanner;

@Component
@Slf4j
public class IntelligentResolverVulExp {
    @Autowired GptRpcProxy gptRpcProxy;
    @Autowired
    BasicInfo basicInfo;

    public String rankEXP(SootMethod callSiteMethod, String riskyAPIMethodSig, String callStack) {

        // 获得sourcecode,输入jar只能找到jimple表示的
        StringBuilder sourceCode = new StringBuilder();

        MethodDescriptor methodDescriptor = MethodDescriptor.getOrCreateDescriptor(callSiteMethod);
        if(methodDescriptor.cfg == null) {
            methodDescriptor.cfg = new CFG(callSiteMethod, true).buildCFG();
        }
        List<TransformableNode> topologicalOrderedTNodesFromCFG = TranUtil.getTopologicalOrderedTNodesFromCFG(methodDescriptor.cfg);
        for(TransformableNode tNode : topologicalOrderedTNodesFromCFG) {
            sourceCode.append(tNode.node.toString() + "\n");
            if(tNode.node.toString().contains(riskyAPIMethodSig)) {
                sourceCode.append(" (上面这句与risky function有关)\n ");
            }
        }

        if(sourceCode.toString().isEmpty()) {
            log.warn("没有对应源代码，不访问LLM直接退出");
            return "没有对应源代码，不访问LLM直接退出";
        }

//        // 找call lines
//        Scanner scanner = new Scanner(sourceCode.toString());
//        StringBuilder callLines = new StringBuilder();
//        while (scanner.hasNextLine()) {
//            String line = scanner.nextLine();
//            if(line.contains(riskyAPIMethodSig)) {
//                callLines.append(line).append(" | ");
//            }
//        }
//        scanner.close();

        return gptRpcProxy.ai_rank_exp(basicInfo.vulDescription, basicInfo.downstreamComponent, basicInfo.upstreamComponent, basicInfo.riskyClass + "." + basicInfo.riskyMethod,
                callStack, sourceCode.toString());
    }
}
