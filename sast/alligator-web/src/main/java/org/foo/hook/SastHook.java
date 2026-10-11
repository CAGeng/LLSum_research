package org.foo.hook;


import app.importer.entity.SourceRule;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.foo.model.ResponseInfo;
import org.foo.service.GithubService;
import org.foo.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Api(tags = "Sast Hook Management")
@RestController
@RequestMapping("/hook")
public class SastHook {

    @Autowired
    GithubService githubService;
    @ApiOperation(value = "hook github event")
    @PostMapping("/github/events")
    ResponseInfo getEvents(@RequestBody String jsonStr){
        System.out.println("json : " + jsonStr );
        githubService.analysisGithubEvent(jsonStr);
        return new ResponseInfo().success("");
    }

}
