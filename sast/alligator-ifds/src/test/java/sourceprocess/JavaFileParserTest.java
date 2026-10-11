package sourceprocess;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaFileParserTest {

    @Test
    void testParseJavaFile() throws Exception {
        // 1. 准备测试用的 Java 文件路径
        Path testFilePath = Paths.get("src/test/resources/TestClass.java");

        // 2. 创建 JavaFileParser 实例并解析文件
        JavaFileParser parser = new JavaFileParser();
        List<MethodInfo> methods = parser.parse(testFilePath);

        // 打印所有解析出的方法信息
        System.out.println("解析出的方法信息：");
        for (MethodInfo method : methods) {
            System.out.println("类名: " + method.getClassName());
            System.out.println("方法名: " + method.getMethodName());
            System.out.println("参数: " + method.getParameters());
            System.out.println("源码: " + method.getSourceCode());
            System.out.println("文件路径: " + method.getFilePath());
            System.out.println("-----------------------------");
        }

    }
}