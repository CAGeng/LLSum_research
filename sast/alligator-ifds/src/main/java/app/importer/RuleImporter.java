package app.importer;

import TranModel.Rules.GenericPropagationRule;
import TranModel.Rules.GenericSourceRule;
import app.configuration.GeneralConfig;
import app.importer.config.RulesConfig;
import app.importer.entity.*;
import detect.dataflow.RuleCollection;
import detect.knowledge.sink.GenericSink;
import detect.knowledge.sink.MethodSinksCollection;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */
@Slf4j
@Component
public class RuleImporter {

    @Autowired
    RulesConfig rulesConfig;

    @Autowired
    RuleContext ruleContext;

    @Autowired
    MethodSinksCollection methodSinksCollection;

    @Autowired
    RuleCollection ruleCollection;

    @Autowired
    GeneralConfig generalConfig;

    @PostConstruct
    private void init(){
        SAXReader reader = new SAXReader();
        try {
            // customized rule
            if (rulesConfig.rulesCorePathSwitcher){
                Resource resource = new ClassPathResource(rulesConfig.rulesCorePath);
                try (InputStream inputStream = resource.getInputStream()) {
                    // 使用 SAXReader 或其他 XML 解析器从 InputStream 读取
                    Document coreDocument = reader.read(inputStream);
                    // 在这里处理 coreDocument
                    Element root = coreDocument.getRootElement();

//                    log.warn("我把core规则集里的src/sink注释了！暂时只用propagete");
                    Element sourceRulesElement = root.element("SourceRules");
                    List<Element> sourceRuleElements = sourceRulesElement.elements("SourceRule");
                    importSourceRule(sourceRuleElements);
                    Element sinkRulesElement = root.element("SinkRules");
                    List<Element> sinkRuleElements = sinkRulesElement.elements("SinkRule");
                    importSinkRule(sinkRuleElements);
                    Element propagationRulesElement = root.element("PropagationRules");
                    List<Element> propagationRuleElements = propagationRulesElement.elements("PropagationRule");
                    importPropagationRule(propagationRuleElements);
                } catch (IOException e) {
                    // 处理异常
                    e.printStackTrace();
                }
            }

            if (rulesConfig.rulesExtendPathSwitcher){
                Resource resource = new ClassPathResource(rulesConfig.rulesExtendPath);
//                File ruleCorefile = resource.getFile();
//                Document coreDocument = reader.read(ruleCorefile);
                // 使用 InputStream 代替 File
                try (InputStream inputStream = resource.getInputStream()) {
                    // 使用 SAXReader 或其他 XML 解析器从 InputStream 读取
                    Document coreDocument = reader.read(inputStream);
                    // 在这里处理 coreDocument
                    Element root = coreDocument.getRootElement();

                    // 临时注释掉
//                    log.warn("我把core规则集里的src/sink注释了！暂时只用propagete");
                    Element sourceRulesElement = root.element("SourceRules");
                    List<Element> sourceRuleElements = sourceRulesElement.elements("SourceRule");
                    importSourceRule(sourceRuleElements);
                    Element sinkRulesElement = root.element("SinkRules");
                    List<Element> sinkRuleElements = sinkRulesElement.elements("SinkRule");
                    importSinkRule(sinkRuleElements);
                    Element propagationRulesElement = root.element("PropagationRules");
                    List<Element> propagationRuleElements = propagationRulesElement.elements("PropagationRule");
                    importPropagationRule(propagationRuleElements);
                } catch (IOException e) {
                    // 处理异常
                    e.printStackTrace();
                }
            }

            if (rulesConfig.rulesAddedPathSwitcher){
                Resource resource = new ClassPathResource(rulesConfig.rulesAddedPath);
//                File ruleCorefile = resource.getFile();
//                Document coreDocument = reader.read(ruleCorefile);
                // 使用 InputStream 代替 File
                try (InputStream inputStream = resource.getInputStream()) {
                    // 使用 SAXReader 或其他 XML 解析器从 InputStream 读取
                    Document coreDocument = reader.read(inputStream);
                    // 在这里处理 coreDocument
                    Element root = coreDocument.getRootElement();

                    Element sourceRulesElement = root.element("SourceRules");
                    if(sourceRulesElement != null) {
                        List<Element> sourceRuleElements = sourceRulesElement.elements("SourceRule");
                        importSourceRule(sourceRuleElements);
                    }

                    Element sinkRulesElement = root.element("SinkRules");
                    if(sinkRulesElement != null) {
                        List<Element> sinkRuleElements = sinkRulesElement.elements("SinkRule");
                        importSinkRule(sinkRuleElements);
                    }
                    Element propagationRulesElement = root.element("PropagationRules");
                    if(propagationRulesElement != null) {
                        List<Element> propagationRuleElements = propagationRulesElement.elements("PropagationRule");
                        importPropagationRule(propagationRuleElements);
                    }
                } catch (IOException e) {
                    // 处理异常
                    e.printStackTrace();
                }
            }

            // thirdParty rule
            if (rulesConfig.rulesCoreThirdPartyPathSwitcher){
                Resource thirdPartyCoreResource = new ClassPathResource(rulesConfig.rulesCoreThirdPartyPath);
                importThirdPartyRule(reader, thirdPartyCoreResource);
            }

            if (rulesConfig.rulesExtendThirdPartyPathSwitcher){
                Resource thirdPartyExtendResource = new ClassPathResource(rulesConfig.rulesExtendThirdPartyPath);
                importThirdPartyRule(reader, thirdPartyExtendResource);
            }

            if (rulesConfig.rulesAddedThirdPartyPathSwitcher){
                Resource thirdPartyExtendResource = new ClassPathResource(rulesConfig.rulesAddedThirdPartyPath);
                importThirdPartyRule(reader, thirdPartyExtendResource);
            }

            // inject sink & source & propagation
            injectSink();
            injectSource();
            injectPropagation();

            log.error("导入source rule: " + ruleContext.getSourceRules().size());
            log.error("导入sink rule: " + ruleContext.getSinkRules().size());
            log.error("导入propagation rule: " + ruleContext.getPropagationRules().size());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void importThirdPartyRule(SAXReader reader, Resource thirdPartyCoreResource) throws IOException, DocumentException {
        try (InputStream inputStream = thirdPartyCoreResource.getInputStream()) {
            // 使用 SAXReader 或其他 XML 解析器从 InputStream 读取
            Document thirdPartyCoreDocument = reader.read(inputStream);
            Element thirdPartyRoot = thirdPartyCoreDocument.getRootElement();
            Element rulesElement = thirdPartyRoot.element("Rules");
            Element ruleDefinitionsElement = rulesElement.element("RuleDefinitions");
            // 临时注释掉
//            log.warn("我把third party core规则集里的src/sink注释了！暂时只用propagete");
            List<Element> dataflowSourceRuleElements = ruleDefinitionsElement.elements("DataflowSourceRule");
            importSourceRule(dataflowSourceRuleElements);
            List<Element> dataflowSinkRuleElements = ruleDefinitionsElement.elements("DataflowSinkRule");
            importSinkRule(dataflowSinkRuleElements);
            List<Element> dataflowPassthroughRuleElements = ruleDefinitionsElement.elements("DataflowPassthroughRule");
            importPropagationRule(dataflowPassthroughRuleElements);
        } catch (IOException e) {
            // 处理异常
            e.printStackTrace();
        }

    }

    private void injectSink(){
        List<SinkRule> sinkRules = ruleContext.getSinkRules();
        for (SinkRule sinkRule : sinkRules){
            GenericSink sink = new GenericSink();
            sink.setSinkRule(sinkRule);
            sink.setRulesConfig(rulesConfig);
            methodSinksCollection.addMethodSinks(sinkRule.getRuleID(), sink);
        }
    }

    private void injectSource(){
        List<SourceRule> sourceRules = ruleContext.getSourceRules();
        for (SourceRule sourceRule : sourceRules){
            GenericSourceRule source = new GenericSourceRule();
            source.setSourceRule(sourceRule);
            ruleCollection.addSourceRule(sourceRule.getRuleId(), source);
        }
    }

    private void injectPropagation(){
        List<PropagationRule> propagationRules = ruleContext.getPropagationRules();
        for (PropagationRule propagationRule : propagationRules){
            GenericPropagationRule propagation= new GenericPropagationRule();
            propagation.setPropagationRule(propagationRule);
            propagation.setGeneralConfig(generalConfig);
            ruleCollection.addPropagationRule(propagationRule.getRuleId(), propagation);
        }
    }

    private void importSinkRule(List<Element> sinkRuleElements) {
        List<SinkRule> sinkRules = ruleContext.getSinkRules();
        for (Element ele : sinkRuleElements) {

            FunctionIdentifier functionIdentifier = getFunctionIdentifier(ele);
            // skip when there is ClassLabel or FunctionLabel
            if (StringUtils.isNotEmpty(functionIdentifier.getClassLabel()) || StringUtils.isNotEmpty(functionIdentifier.getFunctionLabel())){
                continue;
            }

            SinkRule sinkRule = new SinkRule();
            sinkRule.setFunctionIdentifier(functionIdentifier);

            sinkRule.setFunctionIdentifier(functionIdentifier);

            Element ruleIdElement = ele.element("RuleID");
            String ruleId = ruleIdElement.getText();
            sinkRule.setRuleID(ruleId);

            Element vulCategoryElement = ele.element("VulnCategory");
            String vulCategory = vulCategoryElement.getText();
            sinkRule.setVulCategory(vulCategory);

            Element sinkElement = ele.element("Sink");
            Sink sink = new Sink();
            Element inArgumentsElement = sinkElement.element("InArguments");
            String inArguments = inArgumentsElement.getText();
            sink.setInArguments(inArguments.split(","));

            Element conditionalElement = sinkElement.element("Conditional");
            sink.setConditional(conditionalElement);

            sinkRule.setSink(sink);

            sinkRules.add(sinkRule);
        }
    }

    private void importSourceRule(List<Element> sourceRuleElements) {
        List<SourceRule> sourceRules = ruleContext.getSourceRules();
        for (Element ele : sourceRuleElements) {

            FunctionIdentifier functionIdentifier = getFunctionIdentifier(ele);
            // skip when there is ClassLabel
            if (StringUtils.isNotEmpty(functionIdentifier.getClassLabel()) || StringUtils.isNotEmpty(functionIdentifier.getFunctionLabel())){
                continue;
            }

            SourceRule sourceRule = new SourceRule();
            sourceRule.setFunctionIdentifier(functionIdentifier);

            Element ruleIdElement = ele.element("RuleID");
            String ruleId = ruleIdElement.getText();
            sourceRule.setRuleId(ruleId);

            Element taintFlagsElement = ele.element("TaintFlags");
            if(taintFlagsElement != null){
                String taintFlags = taintFlagsElement.getText();
                sourceRule.setTaintFlags(taintFlags.split(","));
            }
            Element outArgumentsElement = ele.element("OutArguments");
            String outArguments = outArgumentsElement.getText();
            sourceRule.setOutArguments(outArguments.split(","));

            sourceRules.add(sourceRule);
        }
    }


    private void importPropagationRule(List<Element> propagationRuleElements) {
        List<PropagationRule> propagationRules = ruleContext.getPropagationRules();
        for (Element ele : propagationRuleElements) {

            FunctionIdentifier functionIdentifier = getFunctionIdentifier(ele);
            // skip when there is ClassLabel
            if (StringUtils.isNotEmpty(functionIdentifier.getClassLabel()) || StringUtils.isNotEmpty(functionIdentifier.getFunctionLabel())){
                continue;
            }

            PropagationRule propagationRule = new PropagationRule();
            propagationRule.setFunctionIdentifier(functionIdentifier);

            Element ruleIdElement = ele.element("RuleID");
            String ruleId = ruleIdElement.getText();
            propagationRule.setRuleId(ruleId);

            Element inArgumentsElement = ele.element("InArguments");
            String inArguments = inArgumentsElement.getText();
            propagationRule.setInArguments(inArguments.split(","));

            Element outArgumentsElement = ele.element("OutArguments");
            String outArguments = outArgumentsElement.getText();
            propagationRule.setOutArguments(outArguments.split(","));



            propagationRules.add(propagationRule);
        }
    }

    private static FunctionIdentifier getFunctionIdentifier(Element ele) {
        Element functionIdentifierElement = ele.element("FunctionIdentifier");
        FunctionIdentifier functionIdentifier = new FunctionIdentifier();
        Element namespaceNameElement = functionIdentifierElement.element("NamespaceName");
        Element classNameElement = functionIdentifierElement.element("ClassName");
        Element functionNameElement = functionIdentifierElement.element("FunctionName");

        Form namespaceNameForm = getForm(namespaceNameElement);
        Form classNameForm = getForm(classNameElement);
        Form functionNameForm = getForm(functionNameElement);

        functionIdentifier.setNamespaceName(namespaceNameForm);
        functionIdentifier.setClassName(classNameForm);
        functionIdentifier.setFunctionName(functionNameForm);

        Element parametersElement = functionIdentifierElement.element("Parameters");
        if (parametersElement != null){
            List<Element> paramTypeElements = parametersElement.elements("ParamType");
            List<String> list = new ArrayList<>();
            for (Element elem : paramTypeElements){
                list.add(elem.getText());
            }
            functionIdentifier.setParameters(list.toArray(new String[list.size()]));
        }

        Element applyToElement = functionIdentifierElement.element("ApplyTo");

        if (applyToElement != null){
            boolean tagImplements = applyToElement.attributeValue("implements").equals("true") ? true:false;
            boolean tagOverrides = applyToElement.attributeValue("overrides").equals("true") ? true:false;
            boolean tagExtends = applyToElement.attributeValue("extends").equals("true") ? true:false;
            ApplyTo applyTo = ApplyTo.builder().tagImplements(tagImplements).tagOverrides(tagOverrides).tagExtends(tagExtends).build();
            functionIdentifier.setApplyTo(applyTo);
        }

        Element classLabelElement = functionIdentifierElement.element("ClassLabel");
        if (classLabelElement != null){
            String classLabel = classLabelElement.getText();
            functionIdentifier.setClassLabel(classLabel);
        }

        Element functionLabelElement = functionIdentifierElement.element("FunctionLabel");
        if (functionLabelElement != null){
            String functionLabel = functionLabelElement.getText();
            functionIdentifier.setFunctionLabel(functionLabel);
        }

        return functionIdentifier;
    }

    private static Form getForm(Element classNameElement) {
        Form classNameForm = new Form();
        Element classNameValue = classNameElement.element("Value");
        if(classNameValue != null){
            classNameForm.setValue(classNameValue.getText());
        }
        Element classNamePattern = classNameElement.element("Pattern");
        if(classNamePattern != null){
            classNameForm.setPattern(classNamePattern.getText());
        }
        return classNameForm;
    }
}
