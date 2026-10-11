package app;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.util.List;

/**
 * User: luca
 * Date: 2023/10/18
 * Description:
 */
public class Transformer {

    public static void main(String[] args) throws Exception {
        SAXReader reader = new SAXReader();
        String coreFilePath = "tmp/java-core.xml";
        try {
            Resource resource = new ClassPathResource(coreFilePath);
            File ruleCorefile = resource.getFile();
            Document coreDocument = reader.read(ruleCorefile);
            Element root = coreDocument.getRootElement();
            transformCore(root);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformCore(Element root){

        Element rulesElement = root.element("Rules");
        Element ruleDefinitionsElement = rulesElement.element("RuleDefinitions");
        List<Element> dataflowSourceRuleElement = ruleDefinitionsElement.elements("DataflowSourceRule");

        for (Element ele : dataflowSourceRuleElement) {
            Element ruleIdElement = ele.element("RuleID");
            System.out.println(ruleIdElement.getText());
        }

        System.out.println("Luca");
    }

}
