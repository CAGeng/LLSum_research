package detect.vulexp.datastruct;

import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

@Component
public class BasicInfo {
    public String upstreamComponent, downstreamComponent;
    public String vulDescription;
    public String CVEID;

    public List<String> downstreamPkgNames = new LinkedList<>();
    public List<String> upstreamPkgNames = new LinkedList<>();

    // 触发点类名
    public String riskyClass;

    // 触发点方法名
    public String riskyMethod;
}
