package detect.knowledge.entry;

import app.context.SpringContextUtils;
import app.importer.config.RulesConfig;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.io.InputStream;
import java.util.*;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

public class FromXmlEntrySet extends HasParameterEntrySet {

    @Override
    public void detectEntries() {

        RulesConfig rulesConfig = SpringContextUtils.getBean(RulesConfig.class);
        if(rulesConfig.rulesEntrySwitcher) {
            try {
                // Load and parse the XML file
                Resource resource = new ClassPathResource(rulesConfig.rulesEntryPath);
                InputStream inputStream = resource.getInputStream();

                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(inputStream);

                // Normalize the XML Structure
                doc.getDocumentElement().normalize();

                // Get all EntryRule nodes
                NodeList entryRuleList = doc.getElementsByTagName("EntryRule");

                for (int i = 0; i < entryRuleList.getLength(); i++) {
                    Node entryRuleNode = entryRuleList.item(i);

                    if (entryRuleNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element entryRuleElement = (Element) entryRuleNode;

                        // Extract ClassName and FunctionName
                        String namespaceName = entryRuleElement.getElementsByTagName("NamespaceName").item(0).getTextContent().trim();
                        String className = entryRuleElement.getElementsByTagName("ClassName").item(0).getTextContent().trim();
                        String functionName = entryRuleElement.getElementsByTagName("FunctionName").item(0).getTextContent().trim();

//                        System.out.println("Class Name: " + className);
//                        System.out.println("Function Name: " + functionName);

                        // Extract Parameters
                        NodeList paramTypeList = entryRuleElement.getElementsByTagName("Parameters").item(0).getChildNodes();
                        int parameterCount = 0;

                        for (int j = 0; j < paramTypeList.getLength(); j++) {
                            Node paramNode = paramTypeList.item(j);
                            if (paramNode.getNodeType() == Node.ELEMENT_NODE && paramNode.getNodeName().equals("ParamType")) {
                                parameterCount++;
                            }
                        }

//                        System.out.println("Number of parameters: " + parameterCount);

                        SootMethod sootMethod = getSootMethod(namespaceName + "." + className, functionName, parameterCount);
                        if(sootMethod == null) continue;

                        System.out.println("从xml导入entry: " + sootMethod.getSignature());

                        // Extract InputTaintArguments
                        NodeList inputTaintArgsList = entryRuleElement.getElementsByTagName("InputTaintArguments").item(0).getChildNodes();
                        List<Integer> taintArguments = new ArrayList<>();

                        for (int j = 0; j < inputTaintArgsList.getLength(); j++) {
                            Node argNode = inputTaintArgsList.item(j);
                            if (argNode.getNodeType() == Node.ELEMENT_NODE) {
                                Element argElement = (Element) argNode;
                                int argIndex = Integer.parseInt(argElement.getTextContent());
                                taintArguments.add(argIndex);
                            }
                        }

                        // Store in the methodInputMap
                        methodInputMap.put(sootMethod, taintArguments);

                        entries.add(sootMethod);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    private SootMethod getSootMethod(String className, String methodName, int paramCount) {
       for(SootClass sootClass : Scene.v().getClasses()) {
           if(sootClass.getName().equals(className)) {
               for(SootMethod sootMethod : sootClass.getMethods()) {
                   if(sootMethod.getName().equals(methodName)) {
                       if(sootMethod.getParameterCount() == paramCount) return sootMethod;
                   }
               }
           }
       }
       return null;
    }
}

