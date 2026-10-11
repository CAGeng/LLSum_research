package detect.knowledge.entry.webapp;

import basic.util.StaticAnalyzeUtils.AnnotationUtil;
import detect.knowledge.entry.EntrySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ServletEntrySet extends EntrySet {
    public HashMap<SootMethod, String> entryMapURL = new HashMap<>();
    private List<String> servletMethods = new ArrayList<>();

    @PostConstruct
    private void init(){
        servletMethods.add("doGet");
        servletMethods.add("doPost");
        servletMethods.add("doHead");
        servletMethods.add("doPut");
        servletMethods.add("doDelete");
        servletMethods.add("doOptions");
        servletMethods.add("doTrace");
        servletMethods.add("service");
        servletMethods.add("init");
        servletMethods.add("destroy");
    }

    @Override
    public void detectEntries() {
        HashSet<SootClass> servletClasses = new HashSet<>();
        HashMap<SootClass, String> servletClassURL = new HashMap<>();
        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            HashMap<String, List<String>> annotationTypeAndValues = AnnotationUtil.getAnnotationTypeAndValues(sootClass);
            for(String annotationType : annotationTypeAndValues.keySet()) {
                if(annotationType.equals("Ljavax/servlet/annotation/WebServlet;")) {
//                    log.error("Servlet class: " + sootClass.getName());
                    servletClasses.add(sootClass);
                    List<String> values = annotationTypeAndValues.get(annotationType);
                    for(String value : values) {
//                        log.error("Servlet url: " + value);
                        servletClassURL.put(sootClass, value);
                    }
                }
            }
        }
        log.error("Servlet class 总数: " + servletClasses.size());

        for(SootClass sootClass : servletClasses) {

            // OWASP benchmark数据集太大，分割一下
//            if(!split_benchmark(sootClass.getName())) continue;

            String url = "unknown";
            if(servletClassURL.containsKey(sootClass)) {
                url = servletClassURL.get(sootClass);
            }
            for(SootMethod sootMethod : sootClass.getMethods()) {
                if(servletMethods.contains(sootMethod.getName())) {
                    entries.add(sootMethod);
                    entryMapURL.put(sootMethod, url);
                }
            }
        }
    }

    // 测试用，只用benchmark一部份数据
    public boolean split_benchmark(String inputString){
        Pattern pattern = Pattern.compile("\\d{5}$"); // 匹配字符串末尾的五位数字
        Matcher matcher = pattern.matcher(inputString);

        if (matcher.find()) {
            String matchedDigits = matcher.group(); // 提取匹配的数字
            int intValue = Integer.parseInt(matchedDigits); // 转化为整数
            if(intValue <= 20 && intValue > 0) return true;
        } else {
            return false;
        }
        return false;

    }
}
