package org.foo.service;

import org.foo.data.AnalysisResult;
import org.foo.model.AnalysisRequest;

import java.util.Map;

public interface IFDSService {

    String analyze(AnalysisRequest analysisRequest);
    String analyze_2(AnalysisRequest analysisRequest);

    /** 漏洞可利用性分析 **/
    String exploitAnalysis(AnalysisRequest analysisRequest);

    String getNDayTriggerPoint();

    String getAnalysisResultByID(Long id);

    String getTotal();

    String getAnalysisResultJsonByID(Long id);

}
