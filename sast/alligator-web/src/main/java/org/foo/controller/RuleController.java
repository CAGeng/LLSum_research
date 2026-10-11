package org.foo.controller;


import app.importer.entity.SourceRule;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.foo.model.ResponseInfo;
import org.foo.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Api(tags = "Rule Management")
@RestController
@RequestMapping("/rule")
public class RuleController {

    @Autowired
    RuleService ruleService;

    @ApiOperation(value = "Add Source Rule")
    @PostMapping("/source_rules/")
    ResponseInfo addSourceRule(@RequestBody SourceRule sourceRule){
        return new ResponseInfo().success(ruleService.addSourceRule(sourceRule));
    }

}
