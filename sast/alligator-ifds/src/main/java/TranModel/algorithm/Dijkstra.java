package TranModel.algorithm;

import TranModel.datastruct.TaintEdge;
import TranModel.datastruct.TaintNode;

import java.util.*;

public class Dijkstra {
    public TaintNode source;
    public TaintNode target;

    public Dijkstra(TaintNode src, TaintNode tar) {
        source = src;
        target = tar;
    }

    private HashMap<TaintNode, Integer> node_distance = new HashMap<>();
    private HashMap<TaintNode, TaintNode> minDistance_pre = new HashMap<>();
    private HashSet<TaintNode> visited = new HashSet<>();

    /*
        return: 是否找到path src->target
     */
    public boolean run() {
        node_distance.put(source, 0);
        visited.add(source);
        HashSet<TaintNode> newVisits = new HashSet<>();
        newVisits.add(source);

        while(!visited.contains(target) && !newVisits.isEmpty()) {
            // 用newVisit更新邻边
            for(TaintNode newVisit : newVisits) {
                int distance = node_distance.get(newVisit);
                for (TaintEdge taintEdge : newVisit.outs) {
                    TaintNode outNode = taintEdge.to;
                    if (!node_distance.containsKey(outNode) || node_distance.get(outNode) > distance + 1) {
                        node_distance.put(outNode, distance + 1);
                        minDistance_pre.put(outNode, newVisit);
                    }
                }
            }

            // visit节点组的最近邻加入visit组
            int minDistance = Integer.MAX_VALUE;
            // 由于是无权图，一次添加多个newvisit是一种时间优化
            newVisits = new HashSet<>();
            for(TaintNode taintNode : visited) {
                for(TaintEdge taintEdge : taintNode.outs) {
                    TaintNode outNode = taintEdge.to;
                    if(visited.contains(outNode)) continue;
                    if(node_distance.containsKey(outNode) && node_distance.get(outNode) < minDistance) {
                        minDistance = node_distance.get(outNode);
                        newVisits = new HashSet<>();
                        newVisits.add(outNode);
                    } else if(node_distance.containsKey(outNode) && node_distance.get(outNode) == minDistance) {
                        newVisits.add(outNode);
                    }
                }
            }

            visited.addAll(newVisits);
        }

        return visited.contains(target);
    }

    public HashMap<TaintNode, Integer> getAllVisitedMapDistance() {
        HashMap<TaintNode, Integer> ret = new HashMap<>();
        for(TaintNode taintNode : visited) {
            ret.put(taintNode, node_distance.get(taintNode));
        }
        return ret;
    }

    /*
        获得找到的数据链路，从target反向找"最短路前驱"
     */
    public List<TaintNode> getSrcTargetPath() {
        List<TaintNode> path = new LinkedList<>();
        assert visited.contains(target);
        TaintNode it = target;
        while (true) {
            path.add(it);
            if(minDistance_pre.get(it) == null) break;
            it = minDistance_pre.get(it);
        }
        assert it == source;
        Collections.reverse(path);
        return path;
    }
}
