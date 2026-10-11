package org.foo.service.impl;

import app.configuration.GeneralConfig;
import app.configuration.SootConfig;
import app.context.SpringContextUtils;
import basic.util.LogUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import detect.DetectContext;
import detect.VulDetector;
import lombok.extern.slf4j.Slf4j;
import org.foo.data.AnalysisResult;
import org.foo.mapper.IAnalysisResultMapper;
import org.foo.mapper.TriggerPointMapper;
import org.foo.model.AnalysisRequest;
import org.foo.service.IFDSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
public class IFDSServiceImpl implements IFDSService {

    public String outputDir = "/Users/fortsun/Desktop/git/restfuzz-sast/output/web";

    @Autowired
    SootConfig sootConfig;

    @Autowired
    GeneralConfig generalConfig;

    @Autowired
    IAnalysisResultMapper iAnalysisResultMapper;

    @Autowired
    TriggerPointMapper triggerPointMapper;

    @Override
    public String analyze(AnalysisRequest analysisRequest) {

        List<String> sootInputPaths = Arrays.asList(analysisRequest.getProjectPath().split(","));
        String vultype = analysisRequest.getVulType();
        generalConfig.setSootInputPaths(sootInputPaths);
        sootConfig.init();
        VulDetector vulDetector = (VulDetector) SpringContextUtils.getBean("vulDetector");
        HashMap<String, List<DetectContext>> res = vulDetector.detect();
        SpringContextUtils.destroyBean(vulDetector);
        if(vultype != null) {
            // 某种类型的漏洞
            List<DetectContext> detectContexts = res.get(vultype);
            return LogUtil.toJsonString(detectContexts);
        }

        return LogUtil.toJsonString(res);
    }

    @Override
    public String analyze_2(AnalysisRequest analysisRequest) {

        List<String> sootInputPaths = Arrays.asList(analysisRequest.getProjectPath().split(","));
        generalConfig.setSootInputPaths(sootInputPaths);
        Long last_id = iAnalysisResultMapper.selectLastInsertId();
        if(last_id == null) last_id = 0L;
        Long new_id = last_id + 1;
        generalConfig.setSootOutputPath(outputDir + "/" + new_id);
        generalConfig.setSootOutputFilename("Analyze_result.json");
        Path path = Paths.get(outputDir + "/" + new_id);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        sootConfig.init();
        VulDetector vulDetector = (VulDetector) SpringContextUtils.getBean("vulDetector");
        HashMap<String, List<DetectContext>> res = vulDetector.detect();
        SpringContextUtils.destroyBean(vulDetector);

        HashMap<String, AnalysisResult> resultHashMap = new HashMap<>();
        int vul_con_total = 0;
        int vul_dataflow_con_total = 0;
        for(String vulType : res.keySet()) {
            List<DetectContext> detectContexts = res.get(vulType);
            int vul_con = detectContexts.size();
            int vul_dataflow_con = 0;
            for(DetectContext detectContext : detectContexts) {
                if(detectContext.dataFlowResult.isHasDataFlow()) vul_dataflow_con += 1;
            }
            AnalysisResult analysisResult = new AnalysisResult();
            analysisResult.setVulTotalCount(vul_con);
            analysisResult.setVulDataReachableCount(vul_dataflow_con);
            resultHashMap.put(vulType, analysisResult);
            vul_con_total += vul_con;
            vul_dataflow_con_total += vul_dataflow_con;
        }
        AnalysisResult analysisResult = new AnalysisResult();
        analysisResult.setVulTotalCount(vul_con_total);
        analysisResult.setVulDataReachableCount(vul_dataflow_con_total);
        analysisResult.setResultData("haha");
        resultHashMap.put("TOTAL", analysisResult);
        iAnalysisResultMapper.insertAnalysisResult(analysisResult);
        log.info("analysisResult id: {}", analysisResult.getId());

        return LogUtil.toJsonString(resultHashMap);
    }

    @Override
    public String exploitAnalysis(AnalysisRequest analysisRequest) {
        // 切分项目路径
        List<String> sootInputPaths = Arrays.asList(analysisRequest.getProjectPath().split(","));
        generalConfig.setSootInputPaths(sootInputPaths);
        return null;
    }

    @Override
    public String getNDayTriggerPoint() {
        Map<String, Object> res = new HashMap<>();
        res.put("triggerPoint", triggerPointMapper.findAllTriggerPoint());
        return LogUtil.toJsonString(res);
    }

    @Override
    public String getAnalysisResultByID(Long id) {
        AnalysisResult analysisResult = iAnalysisResultMapper.selectAnalysisResultById(id);
        if(analysisResult == null) {
            return LogUtil.toJsonString("no such analysisResult");
        }
        Map<String, Object> res = new HashMap<>();
        res.put("analysisResult", iAnalysisResultMapper.selectAnalysisResultById(id));
        return LogUtil.toJsonString(res);
    }

    @Override
    public String getTotal() {
        Map<String, Object> res = new HashMap<>();
        res.put("total", iAnalysisResultMapper.getSumCount());
        return LogUtil.toJsonString(res);
    }

    @Override
    public String getAnalysisResultJsonByID(Long id) {
        String filePath = outputDir + "/" + id + "/Analyze_result.json";

        try {
            // 读取 JSON 文件
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            // 解析 JSON 数据
            JsonParser jsonParser = new JsonParser();
            JsonObject jsonObject = jsonParser.parse(jsonContent.toString()).getAsJsonObject();

            return jsonObject.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
