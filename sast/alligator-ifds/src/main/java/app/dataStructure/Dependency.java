package app.dataStructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import soot.SootMethod;

import java.util.List;
/**
 * 用于表示组件之间的依赖关系
 * @author 唐亦骁
 */
@Component
@Getter
@Setter
public class Dependency {
    public Dependency() {}

    public Dependency(String purl) {
        this.purl = purl;
    }

    // purl唯一标识符
    private String purl;

    // 当前依赖的父依赖
    private Dependency superDependency;

    // 当前依赖的class地址
    private String projectPath;

    // 当前组件包含的漏洞
    private List<String> vulnerabilities;

    // 该依赖组件所依赖的组件列表
    private List<Dependency> dependencies;


}
