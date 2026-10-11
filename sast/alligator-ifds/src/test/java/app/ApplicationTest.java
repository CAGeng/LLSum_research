package app;

import basic.cfg.CFG;
import basic.cg.CG;
import basic.common.SootInit;
import basic.util.Pair;
import detect.unit.instrument.InstrumentUtil;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;

import java.io.File;
import java.io.IOException;
import java.util.*;

//@SpringBootTest
public class ApplicationTest {


    @Test
    void testConstructCG(){
        String inputPath = "/Users/luca/dev/research/IntelligentSAST-TestCase/target/classes/";
        SootInit.setSoot_inputClass(inputPath);
        List<SootMethod> entryPoints = new LinkedList<>();

        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            if(sootClass.getPackageName().contains("org") && sootClass.getName().contains("Main1")){
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.toString().contains("main")) {
                        System.out.println(sootMethod);
                        entryPoints.add(sootMethod);
                    }
                }
            }
        }
        CG.setSpark(true);
        CG cg = new CG(entryPoints);

        HashSet<SootMethod> hashSet = cg.edgesOutOf(entryPoints.get(0));
        System.out.println(hashSet.size());

        CFG cfg = new CFG(entryPoints.get(0), true).buildCFG();
        for (Unit unit : cfg.allNodes.keySet()){
            System.out.println("unit : " + unit);
            List<SootMethod> sootMethodList = cg.calleeOutOf(unit);
            System.out.println(sootMethodList);

        }

        System.out.println(cg.callGraph);
        System.out.println(cg.callGraph.size());
        System.out.println("Luca");
    }

    @Test
    void testPointTo(){
        String inputPath = "/Users/luca/dev/research/IntelligentSAST-TestCase/target/classes/";
        SootInit.setSoot_inputClass(inputPath);
        List<SootMethod> entryPoints = new LinkedList<>();

        for(SootClass sootClass : Scene.v().getApplicationClasses()) {
            if(sootClass.getPackageName().contains("org.p") && sootClass.getName().contains("PointToAnalysisTest2")){
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.toString().contains("main")) {
                        System.out.println(sootMethod);
                        entryPoints.add(sootMethod);
                    }
                }
            }
        }
        CG.setSpark(true);
        CG cg = new CG(entryPoints);
        HashSet<SootMethod> hashSet = cg.edgesOutOf(entryPoints.get(0));
        System.out.println(hashSet.size());


    }

    @Test
    void detectTaintFlags() throws Exception {
        Set<String> taintFlagSet = new HashSet<>();

        Map<String, Integer> map = new HashMap<>();



        Resource coreResource = new ClassPathResource("rules/java-core.xml");
        extracted(coreResource, taintFlagSet, map);
        Resource extendResource = new ClassPathResource("rules/java-extend.xml");
        extracted(extendResource, taintFlagSet, map);
        System.out.println(taintFlagSet);

        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(map.entrySet());
        Collections.sort(list, (a, b)-> b.getValue() - a.getValue());
        System.out.println(list.size());
        for (int i = 0 ; i < list.size(); i ++){
            Map.Entry<String, Integer> entry = list.get(i);
            System.out.println(entry.getKey() + " : " + entry.getValue());
        }
    }

    private static void extracted(Resource resource, Set<String> taintFlagSet, Map<String, Integer> map) throws IOException, DocumentException {
        File ruleCorefile = resource.getFile();
        SAXReader reader = new SAXReader();
        Document coreDocument = reader.read(ruleCorefile);
        Element root = coreDocument.getRootElement();

        Element rulesElement = root.element("Rules");
        Element ruleDefinitionsElement = rulesElement.element("RuleDefinitions");
        List<Element> dataflowSourceRuleElements = ruleDefinitionsElement.elements("DataflowSourceRule");

        for (Element ele : dataflowSourceRuleElements) {

            Element taintFlagsElement = ele.element("TaintFlags");
            if (taintFlagsElement != null) {
                String taintFlags = taintFlagsElement.getText();
                String[] taintFlagsArr = taintFlags.split(",");
                taintFlagSet.addAll(Arrays.asList(taintFlagsArr));

                for (String taintFlag : taintFlagsArr){
                    if (map.containsKey(taintFlag)){
                        map.put(taintFlag, map.get(taintFlag)+1);
                    }else{
                        map.put(taintFlag, 1);
                    }
                }
            }

        }
    }
}
