package detect.summary;

import app.context.SpringContextUtils;
import basic.DefaultDetector.MethodDescriptor;
import basic.util.SetUtil;
import detect.summary.datastructure.Snapshot;
import intelligent.IntelligentLogger;
import intelligent.IntelligentResolver;
import intelligent.condition.CT;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.*;


public class Differentiation {

    // 返回有差异的参数
    public List<Integer> diffentiate(List<Snapshot> snapshots, SootMethod invokeMethod) {
        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentResolver.class).intelligentLogger;
        // 在这里比较incoming states的差异
        intelligentLogger.logFileRPODiff("方法：" + invokeMethod + " 一共有" + snapshots.size() + "个incoming states");
        List<List<Integer>> allIncomings = new LinkedList<>();
        for (Snapshot snapshot : snapshots) {
            List<Integer> taintedParams1 = snapshot.taintedParams;
            intelligentLogger.logFileRPODiff("[其他incoming] taintedParams: " + taintedParams1);
            allIncomings.add(taintedParams1);
        }

        Map<Integer, String> statusMap = SetUtil.getStatusMap(allIncomings);
        List<Integer> diffParams = new LinkedList<>();
        for (Map.Entry<Integer, String> entry : statusMap.entrySet()) {
            intelligentLogger.logFileRPODiff("参数" + entry.getKey() + "的状态：" + entry.getValue());
            if (entry.getValue().equals("可能出现")) {
                diffParams.add(entry.getKey());
            }
        }
        return diffParams;

    }

    // 返回有差异的CT
    public HashMap<String, String> diffentiateCT(List<Snapshot> snapshots, SootMethod invokeMethod) {
        // 返回两个字符串，分别是different字符串的简单和详细（用于提示词）版本
        IntelligentLogger intelligentLogger = SpringContextUtils.getBean(IntelligentResolver.class).intelligentLogger;
        // 在这里比较incoming states Condition的差异
        intelligentLogger.logCTDiff("方法：" + invokeMethod + " 一共有" + snapshots.size() + "个snapshots");
        HashMap<String, String> incomingStates = new HashMap<>();
        for (Snapshot snapshot : snapshots) {
            HashMap<Integer, CT> param2CT = snapshot.value2CT;
            StringBuilder sb = new StringBuilder();
            StringBuilder sbDetailed = new StringBuilder();
            for(int ind = 0; ind < invokeMethod.getParameterCount(); ind++) {
                CT ct = param2CT.get(ind);
                if(ct != null) {
                    sb.append("param").append(ind).append("-").append(ct.getValidConditionString());
                    sbDetailed.append("param").append(ind).append("-").append(ct.getValidConditionStringDetailed());
                }
            }
            if(sb.length() != 0 && !incomingStates.containsKey(sb.toString())) {
                incomingStates.put(sb.toString(), sbDetailed.toString());
            }
        }

        if(incomingStates.keySet().size() > 1) {
            intelligentLogger.logCTDiff("方法：" + invokeMethod + " 一共有" + incomingStates.size() + "个incoming CT states");
            for (String incomingState : incomingStates.keySet()) {
                intelligentLogger.logCTDiff("incoming CT: " + incomingState);
            }
        }

        return incomingStates;

    }

}
