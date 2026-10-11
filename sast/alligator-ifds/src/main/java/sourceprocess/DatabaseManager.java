package sourceprocess;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private Connection conn;

    public DatabaseManager(String url, String user, String password) throws SQLException {
        conn = DriverManager.getConnection(url, user, password);
        createTable();
    }

    private void createTable() throws SQLException {
        String dropSql = "DROP TABLE IF EXISTS methods;";  // 删除已存在的 methods 表
        String createSql = "CREATE TABLE methods (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "class_name VARCHAR(255) NOT NULL, " +
                "method_name VARCHAR(255) NOT NULL, " +
                "parameters VARCHAR(255), " +
                "source_code LONGTEXT, " +
                "file_path VARCHAR(255), " +
                "INDEX idx_class_method (class_name, method_name));";  // 创建新表

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(dropSql);  // 执行删除表的语句
            stmt.execute(createSql);  // 执行创建表的语句
        }
    }


    public void saveMethods(List<MethodInfo> methods) throws SQLException {
        // 插入 SQL
        String insertSql = "INSERT INTO methods (class_name, method_name, parameters, source_code, file_path) VALUES (?, ?, ?, ?, ?)";
        // 检查是否存在的 SQL
        String checkSql = "SELECT COUNT(*) FROM methods WHERE file_path = ? AND class_name = ? AND method_name = ?";

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSql);
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            for (MethodInfo method : methods) {
                // 检查是否已存在
                checkStmt.setString(1, method.getFilePath());
                checkStmt.setString(2, method.getClassName());
                checkStmt.setString(3, method.getMethodName());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    // 如果已存在，跳过插入
                    continue;
                }

                // 如果不存在，插入新记录
                insertStmt.setString(1, method.getClassName());
                insertStmt.setString(2, method.getMethodName());
                insertStmt.setString(3, method.getParameters());
                insertStmt.setString(4, method.getSourceCode());
                insertStmt.setString(5, method.getFilePath());
                insertStmt.addBatch();
            }

            // 执行批量插入
            insertStmt.executeBatch();
        }
    }

    public List<String> searchMethod(String className, String methodName) throws SQLException {
        String sql = "SELECT source_code FROM methods WHERE class_name = ? AND method_name = ?";
        List<String> results = new ArrayList<>();
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, className);
            pstmt.setString(2, methodName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                results.add(rs.getString("source_code"));
            }
        }
        return results;
    }
}
