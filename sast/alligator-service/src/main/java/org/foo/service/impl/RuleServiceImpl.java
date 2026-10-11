package org.foo.service.impl;

import TranModel.Rules.GenericSourceRule;
import app.importer.entity.Form;
import app.importer.entity.FunctionIdentifier;
import app.importer.entity.SourceRule;
import app.utils.UUIDUtil;
import detect.dataflow.RuleCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.foo.mapper.SourceRuleMapper;
import org.foo.model.db.DBSourceRule;
import org.foo.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * User: luca
 * Date: 2023/11/7
 * Description:
 */

@Slf4j
@Service
public class RuleServiceImpl implements RuleService {

    @Autowired
    SourceRuleMapper sourceRuleMapper;

    @Autowired
    RuleCollection ruleCollection;

    @Override
    public boolean addSourceRule(SourceRule sourceRule) {

        DBSourceRule dbSourceRule = new DBSourceRule();

        String ruleId = UUIDUtil.generateRuleID();
        String[] taintFlags = sourceRule.getTaintFlags();
        String[] outArguments = sourceRule.getOutArguments();
        FunctionIdentifier functionIdentifier = sourceRule.getFunctionIdentifier();
        Form namespaceNameForm = functionIdentifier.getNamespaceName();
        Form classNameForm = functionIdentifier.getClassName();
        Form functionNameForm = functionIdentifier.getFunctionName();

        if (StringUtils.isNotEmpty(namespaceNameForm.getValue())){
            dbSourceRule.setNamespaceName(namespaceNameForm.getValue());
        }else{
            dbSourceRule.setNamespaceName(namespaceNameForm.getPattern());
        }

        if (StringUtils.isNotEmpty(classNameForm.getValue())){
            dbSourceRule.setClassName(classNameForm.getValue());
        }else{
            dbSourceRule.setClassName(classNameForm.getPattern());
        }

        if (StringUtils.isNotEmpty(functionNameForm.getValue())){
            dbSourceRule.setFunctionName(functionNameForm.getValue());
        }else{
            dbSourceRule.setFunctionName(functionNameForm.getPattern());
        }

        dbSourceRule.setRuleID(ruleId);
        dbSourceRule.setTaintFlags(StringUtils.join(taintFlags, ","));

        dbSourceRule.setTagExtends(true);
        dbSourceRule.setTagImplements(true);
        dbSourceRule.setTagOverrides(true);

        dbSourceRule.setOutArguments(StringUtils.join(outArguments, ","));

        sourceRuleMapper.addSourceRule(dbSourceRule);

        //sync with rule collection
        GenericSourceRule genericSourceRule = new GenericSourceRule();
        genericSourceRule.setSourceRule(sourceRule);
        ruleCollection.addSourceRule(ruleId, genericSourceRule);

        return true;
    }
}
