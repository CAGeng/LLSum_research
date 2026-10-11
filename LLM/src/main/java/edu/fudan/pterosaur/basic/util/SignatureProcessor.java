package edu.fudan.pterosaur.basic.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class SignatureProcessor {
    public static Map<String, List<String>> processSignatures(String inputFile) {
        // 用于存储前面签名为 key，后面签名为 List 的 Map
        Map<String, List<String>> signatureMap = new LinkedHashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // 提取 < 开始到 <--- 为止的部分作为 key
                int keyStartIdx = line.indexOf('<');
                int keyEndIdx = line.indexOf("<---");

                if (keyStartIdx != -1 && keyEndIdx != -1) {
                    String keySignature = line.substring(keyStartIdx, keyEndIdx).trim();

                    // 去除 -InterfaceClass 和 -SuperClass
                    keySignature = keySignature.replaceAll("-InterfaceClass|-SuperClass", "");

                    // 提取 <--- 后面的部分作为 value
                    String valueSignature = line.substring(keyEndIdx + 4).trim();

                    // 如果 key 不存在，则初始化为新列表
                    signatureMap.computeIfAbsent(keySignature, k -> new ArrayList<>());

                    // 将 value 添加到对应的 key 的列表中
                    signatureMap.get(keySignature).add(valueSignature);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return signatureMap;
    }
}
