package org.foo.config;

import TranModel.Rules.GenericSourceRule;
import app.importer.entity.ApplyTo;
import app.importer.entity.Form;
import app.importer.entity.FunctionIdentifier;
import app.importer.entity.SourceRule;
import detect.dataflow.RuleCollection;
import org.foo.mapper.SourceRuleMapper;
import org.foo.model.db.DBSourceRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * User: luca
 * Date: 2023/11/7
 * Description:
 */

@Configuration
public class RuleInitConfig {
    @Autowired
    SourceRuleMapper sourceRuleMapper;

    @Autowired
    RuleCollection ruleCollection;

    @PostConstruct
    private void init(){
        List<DBSourceRule> DBSourceRules = sourceRuleMapper.findAllSourceRule();
        for (DBSourceRule dbSourceRule : DBSourceRules){

            ApplyTo applyTo = ApplyTo.builder().tagImplements(dbSourceRule.isTagImplements()).tagOverrides(dbSourceRule.isTagOverrides()).tagExtends(dbSourceRule.isTagExtends()).build();
            FunctionIdentifier functionIdentifier = FunctionIdentifier.builder()
                    .namespaceName(Form.builder().value(dbSourceRule.getNamespaceName()).build())
                    .className(Form.builder().value(dbSourceRule.getClassName()).build())
                    .functionName(Form.builder().value(dbSourceRule.getFunctionName()).build())
                    .applyTo(applyTo)
                    .build();

            SourceRule sourceRule = new SourceRule();
            sourceRule.setRuleId(dbSourceRule.getRuleID());
            sourceRule.setFunctionIdentifier(functionIdentifier);
            sourceRule.setTaintFlags(dbSourceRule.getTaintFlags().split(","));

            GenericSourceRule source = new GenericSourceRule();
            source.setSourceRule(sourceRule);
            ruleCollection.addSourceRule(sourceRule.getRuleId(), source);
        }
    }


}
