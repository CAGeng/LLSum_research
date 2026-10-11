package basic.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SetUtil {

    // 从一系列列表里统计每个整数的出现情况
    public static Map<Integer, String> getStatusMap(List<List<Integer>> lists) {
        // 定义整数范围
        int min = -2;
        int max = 6;

        // 使用一个 Map 来统计每个整数的状态
        Map<Integer, String> statusMap = new HashMap<>();

        // 初始化每个整数的状态为 "可能出现"
        for (int i = min; i <= max; i++) {
            statusMap.put(i, "可能出现");
        }

        // 遍历每个列表
        for (int i = min; i <= max; i++) {
            boolean foundInAll = true; // 假设该数在所有列表中都出现
            boolean foundInNone = true; // 假设该数在所有列表中都没有出现

            for (List<Integer> list : lists) {
                if (list.contains(i)) {
                    foundInNone = false; // 该数在某些列表中出现
                } else {
                    foundInAll = false; // 该数在某些列表中没有出现
                }
            }

            // 根据状态更新
            if (foundInAll) {
                statusMap.put(i, "一定出现");
            } else if (foundInNone) {
                statusMap.put(i, "一定不出现");
            } else {
                statusMap.put(i, "可能出现");
            }
        }

        return statusMap;
    }
}
