package sourceprocess;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MethodInfoService {

    @Autowired
    private MethodInfoRepository methodInfoRepository;

    // 保存方法信息
    public void saveMethods(List<MethodInfo> methods) {
        methodInfoRepository.saveAll(methods);
    }

    // 根据类名和方法名查询源代码
    public List<String> searchMethod(String className, String methodName) {
        List<MethodInfo> methodInfos = methodInfoRepository.findByClassNameAndMethodName(className, methodName);
        return methodInfos.stream().map(MethodInfo::getSourceCode).collect(Collectors.toList());
    }
}

