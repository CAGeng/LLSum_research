package detect.knowledge.entry.discovery;

import app.configuration.GeneralConfig;
import app.importer.config.RulesConfig;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Component
public class CommunicateArgDiscovery {

    @Autowired
    GeneralConfig generalConfig;

    @Autowired
    RulesConfig rulesConfig;

    public HashSet<SootClass> basicCommunicateClasses = new HashSet<>();
    public HashSet<SootClass> extendedCommunicateClasses = new HashSet<>();
    private final HashSet<String> commuClassFromKnowledge = new HashSet<>();

    public void run() {

        System.out.println("CommunicateArgDiscovery start...");

        try {
            // 读取所有行到List中
            String entryCommunicationPath = rulesConfig.entryCommunicationPath;
            Path path = new ClassPathResource(entryCommunicationPath).getFile().toPath();
            List<String> lines = Files.readAllLines(path);
            commuClassFromKnowledge.addAll(lines);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(SootClass sootClass : Scene.v().getClasses()) {
            String typeName = sootClass.getShortName();
            int fl = 0;
            if(typeName.equalsIgnoreCase("request")){
                fl = 1;
            }
            if(typeName.toLowerCase().endsWith("message")){
                fl = 1;
            }
            if(commuClassFromKnowledge.contains(typeName)) {
                fl = 1;
                System.out.println("匹配到知识库中的通信类: " + typeName);
            }
            if(fl == 1) {
                basicCommunicateClasses.add(sootClass);
            }
        }
        System.out.println("basicCommunicateClasses: " + basicCommunicateClasses.size());
        System.out.println(basicCommunicateClasses);

        for(SootClass sootClass : basicCommunicateClasses) {
            HashSet<SootClass> allSubs = ClassUtils.getAllSubs(sootClass);
            extendedCommunicateClasses.addAll(allSubs);
        }
        System.out.println("extendedCommunicateClasses: " + extendedCommunicateClasses.size());

        String outBasicPath = generalConfig.sootOutputPath;
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(
                    Paths.get(outBasicPath).resolve("communication_class.txt").toString()
            ));
            for (SootClass sootClass : extendedCommunicateClasses) {
                writer.write(sootClass.toString());
                writer.newLine();
            }
            writer.close();
        } catch (Exception ignored) {
        }
    }
}
