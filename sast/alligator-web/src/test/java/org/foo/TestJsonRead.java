package org.foo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import detect.DetectContext;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;


public class TestJsonRead {
    @Test
    public void testJsonRead() {

        // 从文件中读取,无法反序列化，不采用
//        HashMap<String, List<DetectContext>> loadedData = loadFromJsonFile("/Users/fortsun/Desktop/git/restfuzz-sast/output/10-30/Analyze_result.json");
//        System.out.println(loadedData);

    }

    public static HashMap<String, List<DetectContext>> loadFromJsonFile(String filePath) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(new File(filePath), new TypeReference<HashMap<String, List<DetectContext>>>() {});
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
