package sourceprocess;

import javax.persistence.*;

@Entity
@Table(name = "methods", indexes = {
        @Index(name = "idx_class_method", columnList = "class_name,method_name"),
        @Index(name = "idx_method_name", columnList = "method_name")
})
public class MethodInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name", nullable = false, length = 255)
    private String className;

    @Column(name = "method_name", nullable = false, length = 255)
    private String methodName;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "source_code", columnDefinition = "LONGTEXT")
    private String sourceCode;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
