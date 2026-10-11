package detect.knowledge.entry.discovery;

import TranModel.datastruct.PayloadStructure;
import app.configuration.GeneralConfig;
import app.importer.config.RulesConfig;
import basic.util.StaticAnalyzeUtils.AnnotationUtil;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import basic.util.StaticAnalyzeUtils.Parameter;
import basic.util.Util;
import detect.knowledge.entry.EntrySet;
import detect.knowledge.entry.spring.SpringEntrySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.tagkit.*;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Component
public class AnnotationEntrySet extends EntrySet {

    @Autowired
    RulesConfig rulesConfig;

    public List<String> entryAnnotationSet = new ArrayList<>();

    @PostConstruct
    private void init(){
        try {
            // 读取所有行到List中
            String entryAnnoPath = rulesConfig.entryAnnoPath;
            Path path = new ClassPathResource(entryAnnoPath).getFile().toPath();
            List<String> lines = Files.readAllLines(path);

            for (String line : lines) {
                String anno = convertToDescriptor(line);
                entryAnnotationSet.add(anno);
            }

            entryAnnotationSet = new ArrayList<>(new HashSet<>(entryAnnotationSet)); // 去重

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String convertToDescriptor(String className) {
        // 1. 将所有的 . 替换为 /
        String withSlashes = className.replace('.', '/');

        // 2. 在开头添加 L 和结尾添加 ;
        return "L" + withSlashes + ";";
    }

    @Override
    public void detectEntries() {
        Scene scene = Scene.v();
        for (SootClass sootClass : scene.getApplicationClasses()){
            List<SootMethod> sootMethods = sootClass.getMethods();
            for (SootMethod sootMethod : sootMethods){
                List<Tag> tagList = sootMethod.getTags();
                for (Tag tag : tagList){
                    if (tag instanceof VisibilityAnnotationTag){
                        ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                        for (AnnotationTag annotationTag : annotationTags){
                            String annotationType = annotationTag.getType();
                            if (entryAnnotationSet.contains(annotationType)){
                                entries.add(sootMethod);
                            }

                        }
                    }
                }
            }
        }

    }
}
