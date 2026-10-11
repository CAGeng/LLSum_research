package app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * User: luca
 * Date: 2023/9/21
 * Description:
 */

@ComponentScan(basePackages={"detect","app","TranModel","intelligent","sourceprocess"})
@SpringBootApplication
@EnableJpaRepositories(basePackages = "sourceprocess")  // 扫描 sourceprocess 包下的所有 Repository
@EntityScan(basePackages = "sourceprocess")  // 确保扫描到 MethodInfo 类
public class IFDSApplication {
    public static void main(String[] args) {

        long startTime = System.currentTimeMillis();

        SpringApplication.run(IFDSApplication.class, args);

        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        System.out.println("应用运行时间（毫秒）: " + executionTime);
    }
}
