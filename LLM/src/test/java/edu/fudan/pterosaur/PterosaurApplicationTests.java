package edu.fudan.pterosaur;

import edu.fudan.pterosaur.basic.util.FileUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;

@SpringBootTest
class PterosaurApplicationTests {

    @Test
    void contextLoads() {
    }


    @Test
    void getProportions(){
        // 硬编码路径对
        List<String[]> pathPairs = new ArrayList<>();
        pathPairs.add(new String[]{
                "/Users/luca/dev/2025/mall-swarm/mall-admin/target/a/BOOT-INF/classes",
                "/Users/luca/dev/2025/mall-swarm/mall-admin/target/a/BOOT-INF/lib"
        });
        pathPairs.add(new String[]{
                "/Users/luca/dev/2025/mall-swarm/mall-auth/target/a/BOOT-INF/classes",
                "/Users/luca/dev/2025/mall-swarm/mall-auth/target/a/BOOT-INF/lib"
        });
        pathPairs.add(new String[]{
                "/Users/luca/dev/2025/mall-swarm/mall-monitor/target/a/BOOT-INF/classes",
                "/Users/luca/dev/2025/mall-swarm/mall-monitor/target/a/BOOT-INF/lib"
        });
        pathPairs.add(new String[]{
                "/Users/luca/dev/2025/mall-swarm/mall-portal/target/a/BOOT-INF/classes",
                "/Users/luca/dev/2025/mall-swarm/mall-portal/target/a/BOOT-INF/lib"
        });
        pathPairs.add(new String[]{
                "/Users/luca/dev/2025/mall-swarm/mall-search/target/a/BOOT-INF/classes",
                "/Users/luca/dev/2025/mall-swarm/mall-search/target/a/BOOT-INF/lib"
        });

        for (String[] pair : pathPairs) {
            File file1 = new File(pair[0]);
            File file2 = new File(pair[1]);
            getProportion(file1, file2);
        }
    }


    public static void getProportion(File file1, File file2){

        if (!file1.exists() || !file2.exists()) {
            System.out.println("文件或文件夹不存在，请检查路径");
            return;
        }

        // 计算文件夹大小
        long size1 = FileUtil.calculateSize(file1);
        long size2 = FileUtil.calculateSize(file2);

        try {
            // 计算大小比例
            double ratio = FileUtil.calculateSizeRatio(size1, size2);
            System.out.printf("比例为: %.4f\n", ratio);

            // 将结果保存到文件
            saveRatioToFile("output/size_ratio_log.txt", file1, file2, size1, size2, ratio);

        } catch (ArithmeticException e) {
            System.err.println("错误: " + e.getMessage());
        }

    }


    // 将比例结果存入文件
    public static void saveRatioToFile(String filePath, File file1, File file2, long size1, long size2, double ratio) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = dateFormat.format(new Date());

            String logEntry = String.format("%s | 文件夹1: %s (大小: %d 字节) | 文件夹2: %s (大小: %d 字节) | 大小比例: %.4f\n",
                    timestamp, file1.getAbsolutePath(), size1, file2.getAbsolutePath(), size2, ratio);

            writer.write(logEntry);
            System.out.println("大小比例已成功追加到 " + filePath);
        } catch (IOException e) {
            System.err.println("写入文件时发生错误: " + e.getMessage());
        }
    }



    @Test
    void mavenCentralSearch() throws Exception{
        String groupId = "junit";  // 替换为目标 groupId
        String artifactId = "junit";  // 替换为目标 artifactId
        String queryUrl = String.format(
                "https://search.maven.org/solrsearch/select?q=g:\"%s\"+AND+a:\"%s\"&rows=20&wt=json",
                groupId, artifactId);

        URL url = new URL(queryUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() == 200) {
            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            StringBuilder response = new StringBuilder();
            int c;
            while ((c = reader.read()) != -1) {
                response.append((char) c);
            }

            JSONObject json = new JSONObject(response.toString());
            JSONArray docs = json.getJSONObject("response").getJSONArray("docs");
            if (docs.length() > 0) {
                System.out.println("Project found: ");
                System.out.println("Group ID: " + docs.getJSONObject(0).getString("g"));
                System.out.println("Artifact ID: " + docs.getJSONObject(0).getString("a"));
                System.out.println("Latest Version: " + docs.getJSONObject(0).getString("latestVersion"));
            } else {
                System.out.println("No project found with the given groupId and artifactId.");
            }
        } else {
            System.out.println("Failed to connect to Maven Central");
        }
        conn.disconnect();
    }

}
