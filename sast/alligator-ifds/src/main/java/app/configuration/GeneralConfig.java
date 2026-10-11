package app.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * User: luca
 * Date: 2023/9/13
 * Description:
 */

@Component
@Getter
@Setter
public class GeneralConfig {

    @Autowired
    GeneralList generalList;

    @Value("${soot.output.path}")
    public String sootOutputPath;

    @Value("${soot.additional.path}")
    public String sootAdditionalPath;

    @Value("${soot.output.filename}")
    public String sootOutputFilename;

    @Value("${soot.output.insFilename}")
    public String getSootOutputInsFileName;

    @Value("${vulexp.downstream.jarPath}")
    public String vulexpDownstreamJarPath;

    @Value("${analysis.taintSanitizeSwitcher}")
    public boolean taintSanitizeSwitcher;

    @Value("${analysis.conditionSummarySwitcher}")
    public boolean conditionSummarySwitcher;

    @Value("${analysis.sparkSwitcher}")
    public boolean sparkSwitcher;

    @Value("${analysis.simpleDataPathSwitcher}")
    public boolean simpleDataPathSwitcher;

    @Value("${analysis.onlyDataflowCallStackInter}")
    public boolean onlyDataflowCallStackInter;

    @Value("${analysis.fieldSensitiveSwitcher}")
    public boolean fieldSensitiveSwitcher;

    @Value("${vulexp.upstream.jarPath}")
    public String vulexpUpstreamJarPath;

    public List<String> scannedMybatisXmlDirs;

    public List<String> sootInputPaths;

    public boolean expLLMRank = false;

    @Value("${list.sourceCode}")
    public String srcDir;

    public int summaryLevel = 3;

    @Autowired
    UpstreamConfig upstreamConfig;

    @Value("${analysis.chainTrackMode}")
    public boolean chainTrackMode; // 跟踪指定链

    @Value("${chainTrack.entryClassName}")
    public String entryClassName;
    @Value("${chainTrack.entryMethodName}")
    public String entryMethodName;
    @Value("${chainTrack.sinkClassName}")
    public String sinkClassName;
    @Value("${chainTrack.sinkMethodName}")
    public String sinkMethodName;

    public boolean output_all_apis = true; // 在找到中间层entry之后，输出中会包含所有可达到此entry的api，为了做更上层的可达性分析

    @PostConstruct
    public void init(){
        sootInputPaths = generalList.sootInputPaths;
        scannedMybatisXmlDirs = generalList.scannedMybatisXmlDirs;
        sootOutputPath = sootOutputPath + "/" + System.currentTimeMillis();
        try {
            Files.createDirectories(Paths.get(sootOutputPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
