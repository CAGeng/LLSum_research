package basic.util;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class LogUtil {

    public static void debug(String info){
        if (true)
            System.out.println(info);
    }

    public static void info(String info){
        System.out.println(info);
    }

    public static void outputToJSON(Object object, String outputPath, String outputFileName) {
        String json = "{\"error\" : \"parse_error\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            json = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        // 将 JSON 字符串写入文件
        try (FileWriter fileWriter = new FileWriter(Paths.get(outputPath).resolve(outputFileName).toString())) {
            fileWriter.write(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJsonString(Object object) {
        String json = "{\"error\" : \"parse_error\"}";
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            json = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return json;
    }

    public static void printBox(String text) {
        String[] lines = text.split("\n");
        int maxLength = 0;

        for (String line : lines) {
            if (line.length() > maxLength) {
                maxLength = line.length();
            }
        }

        printLine(maxLength + 4); // 上边框

        for (String line : lines) {
            System.out.printf("| %-" + maxLength + "s |\n", line);
        }

        printLine(maxLength + 4); // 下边框
    }

    public static void printLine(int length) {
        for (int i = 0; i < length; i++) {
            System.out.print("-");
        }
        System.out.println();
    }

}
