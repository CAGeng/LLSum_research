package detect.unit.instrument;

import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;

@Getter
@Setter
public class SinkInstRecord {
    public MethodInstRecord methodInstRecord; // 记录sink点方法
    public HashSet<Integer> pollutedParams = new HashSet<>();   //  应该被污染的参数，用于动态检测这些参数是否被污染
    public boolean flag = true;     // 标记是否为最终可判断gadget可利用的sink点, E.g.对于 ClassLoad 类型，需要多个sink点同时满足，而
                                    // 只有当满足最后一个 sink 点时才可以确定能攻击
}
