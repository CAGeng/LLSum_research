package sourceprocess;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.List;

public class TestSourceProcess {

    public static void main(String[] args) throws SQLException, IOException {
        Path dir = Paths.get("/Users/fortsun/Desktop/git/jetty.project");
        DatabaseManager db = new DatabaseManager("jdbc:mysql://localhost:3306/mydb", "newuser", "newpassword");
        JavaFileParser parser = new JavaFileParser();

        List<Path> javaFiles = FileUtils.findAllJavaFiles(dir);
        for (Path file : javaFiles) {
            List<MethodInfo> methods = parser.parse(file);
            db.saveMethods(methods);
        }

        // 示例查询
        List<String> sources = db.searchMethod("sourceprocess.TestClass", "testMethod");
        sources.forEach(System.out::println);
    }
}
