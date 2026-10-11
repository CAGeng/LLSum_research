package org.foo.controller;


import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.foo.model.AnalysisRequest;
import org.foo.service.IFDSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Api(tags = "Static Analysis Management")
@RestController
@RequestMapping("/sat")
public class SATController {

    @Autowired
    IFDSService ifdsService;

    @ApiOperation(value = "Analysis Results")
    @PostMapping("/analysis/result")
    String analyze(@RequestBody AnalysisRequest analysisRequest){
        return ifdsService.analyze(analysisRequest);
    }

    @PostMapping("/analysis/result_con")
    String analyze_2(@RequestBody AnalysisRequest analysisRequest){
        return ifdsService.analyze_2(analysisRequest);
    }

    /** 漏洞可利用性分析 **/
    @PostMapping("/analysis/exploitDetect")
    String exploitAnalysis(@RequestBody AnalysisRequest analysisRequest) {
        return null;
    }

    @GetMapping("/triggerpoint")
    String getNDayTriggerPoint(){
        return ifdsService.getNDayTriggerPoint();
    }

    @GetMapping("/analysis/result/{id}")
    String getAnalysisResultByID(@PathVariable Long id){
        return ifdsService.getAnalysisResultByID(id);
    }

    @GetMapping("/analysis/result/json/{id}")
    String getAnalysisResultJSONByID(@PathVariable Long id){
        return ifdsService.getAnalysisResultJsonByID(id);
    }

}
