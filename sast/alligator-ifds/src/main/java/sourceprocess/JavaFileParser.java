package sourceprocess;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.Node;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JavaFileParser {
    public List<MethodInfo> parse(Path javaFile) throws IOException {
        // 读取文件内容（兼容 Java 8）
        byte[] fileBytes = Files.readAllBytes(javaFile);
        String sourceCode = new String(fileBytes, StandardCharsets.UTF_8);

        List<MethodInfo> methods = new ArrayList<>();
        // 解析 Java 文件
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                String className = getFullClassName(cls, packageName);
                processMembers(cls, className, javaFile, sourceCode, methods);
            });
//            log.info("解析成功: " + javaFile);
        } catch (Exception e) {
            log.error("解析失败: " + javaFile);
        }

        return methods;
    }

    private String getFullClassName(TypeDeclaration<?> type, String packageName) {
        // 获取外部类名（如果有）
        String parentName = type.getParentNode()
                .filter(parent -> parent instanceof TypeDeclaration)
                .map(parent -> ((TypeDeclaration<?>) parent).getNameAsString())
                .orElse("");

        // 生成完整类名
        String fullName = type.getNameAsString();
        if (!parentName.isEmpty()) {
            // 如果是内部类，拼接外部类名和内部类名
            fullName = parentName + "$" + fullName;
        } else if (!packageName.isEmpty()) {
            // 如果是顶级类，拼接包名和类名
            fullName = packageName + "." + fullName;
        }

        return fullName;
    }

    private void processMembers(TypeDeclaration<?> type, String className, Path filePath, String sourceCode, List<MethodInfo> methods) {
        // 处理方法
        type.getMethods().forEach(method ->
                methods.add(extractMethodInfo(method, className, filePath, sourceCode)));
        // 处理构造方法
        type.getConstructors().forEach(constructor ->
                methods.add(extractConstructorInfo(constructor, className, filePath, sourceCode)));
        // 处理内部类
        type.getMembers().stream()
                .filter(member -> member instanceof TypeDeclaration)
                .forEach(member -> {
                    String innerClassName = getFullClassName((TypeDeclaration<?>) member, ""); // 不传递包名
                    processMembers((TypeDeclaration<?>) member, className + "$" + innerClassName, filePath, sourceCode, methods);
                });
    }

    private MethodInfo extractConstructorInfo(ConstructorDeclaration constructor, String className, Path filePath, String sourceCode) {
        MethodInfo info = new MethodInfo();
        info.setClassName(className);
        info.setMethodName("<init>"); // 构造方法的名称固定为 "<init>"
        info.setParameters(constructor.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(",")));
        info.setSourceCode(extractSource(constructor, sourceCode));
        info.setFilePath(filePath.toString());
        return info;
    }

    private MethodInfo extractMethodInfo(CallableDeclaration<?> method, String className, Path filePath, String sourceCode) {
        MethodInfo info = new MethodInfo();
        info.setClassName(className);
        info.setMethodName(method instanceof ConstructorDeclaration ? "<init>" : method.getNameAsString());
        info.setParameters(method.getParameters().stream()
                .map(p -> p.getType().asString())
                .collect(Collectors.joining(",")));
        info.setSourceCode(extractSource(method, sourceCode));
        info.setFilePath(filePath.toString());
        return info;
    }

    private String extractSource(Node node, String sourceCode) {
        return node.getRange().map(range -> {
            String[] lines = sourceCode.split("\r\n|\n|\r");
            return IntStream.rangeClosed(range.begin.line - 1, range.end.line - 1)
                    .mapToObj(i -> i < lines.length ? lines[i] : "")
                    .collect(Collectors.joining("\n"));
        }).orElse("");
    }
}