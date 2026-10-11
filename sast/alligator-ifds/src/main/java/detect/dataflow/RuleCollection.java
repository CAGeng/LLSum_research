package detect.dataflow;

import TranModel.Rule;
import TranModel.Rules.GenericPropagationRule;
import TranModel.Rules.GenericSourceRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: luca
 * Date: 2023/10/16
 * Description:
 */

@Component
public class RuleCollection {

    @Autowired
    Map<String, Rule> ruleMap;

    private Map<String, Rule> sourceRuleMap = new ConcurrentHashMap<>();

    private Map<String, Rule> propagationRuleMap = new ConcurrentHashMap<>();

    @PostConstruct
    private void init(){
        ruleMap.forEach((key, rule) -> {
            if(rule instanceof GenericSourceRule){
                addSourceRule(key, rule);
            }else if (rule instanceof GenericPropagationRule){
                addPropagationRule(key, rule);
            }
        });
    }

    public Map<String, Rule> getPropagationRuleMap(){
        return propagationRuleMap;
    }

    public Map<String, Rule> getSourceRuleMap(){
        return sourceRuleMap;
    }

    public Map<String, Rule> getRuleMap(){
        Map<String, Rule> map = new LinkedHashMap<>();
        map.putAll(sourceRuleMap);
        map.putAll(propagationRuleMap);
        return map;
    }

    public void addSourceRule(String key, Rule rule){
        sourceRuleMap.put(key, rule);
    }

    public void addPropagationRule(String key, Rule rule){
        propagationRuleMap.put(key, rule);
    }
}
