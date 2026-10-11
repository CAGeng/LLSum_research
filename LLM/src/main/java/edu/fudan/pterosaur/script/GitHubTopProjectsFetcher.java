package edu.fudan.pterosaur.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class GitHubTopProjectsFetcher {

    private static final String GITHUB_API_URL = "https://api.github.com/search/repositories?q=java+商城&sort=stars&order=desc";
    private static final String GITHUB_TOKEN = "";  // 将其替换为你的GitHub个人访问令牌

    public static void main(String[] args) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(GITHUB_API_URL + "&per_page=30")  // 请求前30个项目
                .header("Authorization", "token " + GITHUB_TOKEN)  // 通过令牌进行认证
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            // 使用 Jackson 解析 JSON 响应
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(response.body().string());
            JsonNode items = rootNode.path("items");

            // 确保输出目录存在
            File outputDir = new File("output");
            if (!outputDir.exists()) {
                outputDir.mkdirs();  // 创建 output 目录
            }

            // 写入到文本文件
            FileWriter writer = new FileWriter("output/top.txt");

            // 遍历 GitHub 返回的项目，并写入文本文件中
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                String name = item.get("name").asText();
                String htmlUrl = item.get("html_url").asText();
                int stargazersCount = item.get("stargazers_count").asInt();

                // 写入文件的格式：商城名字 链接 Star数量
                writer.write(String.format("%-30s %-80s %d\n", name, htmlUrl, stargazersCount));
            }

            writer.close();
            System.out.println("Top Java商城项目已经写入output/top.txt文件");
        }
    }
}
