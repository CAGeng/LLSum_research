package edu.fudan.pterosaur.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
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

    @Value("${analysis.onlyOneCallee}")
    public boolean onlyOneCallee;

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

    @Value("${sink.classPath}")
    public String sinkClassPath;

    public List<String> scannedMybatisXmlDirs;

    public List<String> sootInputPaths;

    public boolean expLLMRank = false;

    @Value("${list.sourceCode}")
    public String srcDir;

    public int summaryLevel = 3;

    @PostConstruct
    public void init(){
        sootInputPaths = generalList.sootInputPaths;
        scannedMybatisXmlDirs = generalList.scannedMybatisXmlDirs;
    }
}
