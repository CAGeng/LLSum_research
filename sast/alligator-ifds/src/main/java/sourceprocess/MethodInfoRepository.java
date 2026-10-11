package sourceprocess;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MethodInfoRepository extends JpaRepository<MethodInfo, Long> {
    // 根据类名和方法名查询方法源代码
    List<MethodInfo> findByClassNameAndMethodName(String className, String methodName);
}

