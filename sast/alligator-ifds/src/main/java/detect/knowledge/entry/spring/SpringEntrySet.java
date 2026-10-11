package detect.knowledge.entry.spring;

import TranModel.datastruct.PayloadStructure;
import basic.util.StaticAnalyzeUtils.AnnotationUtil;
import basic.util.StaticAnalyzeUtils.ClassUtils;
import basic.util.StaticAnalyzeUtils.Parameter;
import basic.util.Util;
import detect.knowledge.entry.EntrySet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.tagkit.*;

import javax.annotation.PostConstruct;
import java.util.*;


@Slf4j
@Component
public class SpringEntrySet extends EntrySet {

    public static final String PostMappingAnnotation = "Lorg/springframework/web/bind/annotation/PostMapping;";
    public static final String GetMappingAnnotation = "Lorg/springframework/web/bind/annotation/GetMapping;";
    public static final String RequestMappingAnnotation = "Lorg/springframework/web/bind/annotation/RequestMapping;";
    public static final String DeleteMappingAnnotation = "Lorg/springframework/web/bind/annotation/DeleteMapping;";
    public static final String PutMappingAnnotation = "Lorg/springframework/web/bind/annotation/PutMapping;";
    public static List<String> entryAnnotationNames = Arrays.asList("post", "get", "request", "delete", "put");

    public List<String> entryAnnotationSet = new ArrayList<>();
    public List<String> classAnnotationSet = new ArrayList<>();
    public HashMap<SootMethod, String> entryMapURL = new HashMap<>();
    public HashMap<SootMethod, List<String>> entryMapPayloadKeys = new HashMap<>();

    public HashMap<SootMethod, List<PayloadStructure>> entryMapPayloadStructures = new HashMap<>();

    @PostConstruct
    private void init(){
        entryAnnotationSet.add(PostMappingAnnotation);
        entryAnnotationSet.add(GetMappingAnnotation);
        entryAnnotationSet.add(RequestMappingAnnotation);
        entryAnnotationSet.add(DeleteMappingAnnotation);
        entryAnnotationSet.add(PutMappingAnnotation);
        classAnnotationSet.add("Lorg/springframework/web/bind/annotation/RestController;");
        classAnnotationSet.add("Lorg/springframework/web/bind/annotation/RequestMapping;");
    }

    @Override
    public void detectEntries() {
        Scene scene = Scene.v();
        for (SootClass sootClass : scene.getApplicationClasses()){

            String classURLPath = "";
            HashSet<AnnotationTag> classAnnotations = ClassUtils.getClassAnnotations(sootClass);

            // url path defined in class anno
            for(AnnotationTag annotationTag : classAnnotations) {
                Collection<AnnotationElem> elems = annotationTag.getElems();
                if(!classAnnotationSet.contains(annotationTag.getType())){
                    continue;
                }

                for(AnnotationElem elem : elems) {
                    if(elem instanceof AnnotationArrayElem) {
                        for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()) {
                            if(annotationElem instanceof AnnotationStringElem) {
                                classURLPath = ((AnnotationStringElem) annotationElem).getValue();
                            }
                        }
                    } else if (elem instanceof AnnotationStringElem) {
                        classURLPath = ((AnnotationStringElem) elem).getValue();
                    }
                }
            }

            List<SootMethod> sootMethods = sootClass.getMethods();
            for (SootMethod sootMethod : sootMethods){
                HashMap<String, List<String>> annotationTypeAndValues = AnnotationUtil.getAnnotationTypeAndValues(sootMethod);
                List<Tag> tagList = sootMethod.getTags();
                for (Tag tag : tagList){
                    if (tag instanceof VisibilityAnnotationTag){
                        ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                        for (AnnotationTag annotationTag : annotationTags){
                            String annotationType = annotationTag.getType();
                            boolean similarAnno = false;
                            for(String anno : entryAnnotationNames){
                                if(annotationType.toLowerCase().equals(anno)){
                                    similarAnno = true;
                                    break;
                                }
                            }
                            if (entryAnnotationSet.contains(annotationType) || similarAnno){
                                entries.add(sootMethod);

                                // url path in method anno
                                // 可能有多个注解value，我目前不知道soot能否获取这个信息，因此存为一个列表, 这个列表用字符串表示，||分割
                                List<String> URLs = new LinkedList<>();
                                Collection<AnnotationElem> elems = annotationTag.getElems();
//                                log.error("------------------------------------------   entry: " + sootMethod);
                                for(AnnotationElem elem : elems) {
                                    if(elem instanceof AnnotationArrayElem) {
                                        for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()){
                                            if(annotationElem instanceof AnnotationStringElem) {
                                                String methodPath = ((AnnotationStringElem) annotationElem).getValue();
                                                URLs.add(methodPath);
                                            }
                                        }
                                    }
                                }

                                StringBuilder pathList = new StringBuilder();
                                if(URLs.size() == 1) {
                                    String url = URLs.get(0);
                                    if(!url.equals("") && !url.startsWith("/")){
                                        url = "/" + url;
                                    }
                                    //拼接
                                    String urlPath = classURLPath + url;
                                    pathList.append(urlPath);
                                }
                                else for(String url : URLs){
                                    if(!url.equals("") && !url.startsWith("/")){
                                        url = "/" + url;
                                    }
                                    // 拼接
                                    String urlPath = classURLPath + url;
                                    pathList.append(" || ").append(urlPath);
                                }

                                entryMapURL.put(sootMethod, pathList.toString());

                            }

                        }
                    }
                }
            }
        }

        findPayloads();
    }

    // e.g. public String one(@RequestParam(value = "xml_str") String xmlStr) throws Exception
    // e.g. @PostMapping(value = "/object")
    //    public List<Student> objectParam(@RequestBody Student user)
    public void findPayloads(){
        for(SootMethod entryMethod : entries) {
            List<String> paramAnnos = Parameter.getParamAnnos(entryMethod);
            if(paramAnnos.isEmpty()) continue;

            // 找到param中的key
            entryMapPayloadKeys.put(entryMethod, paramAnnos);

            // 遍历方法参数
            for (int i = 0; i < entryMethod.getParameterCount(); i++) {
                Type paramType = entryMethod.getParameterType(i);
//                log.warn("paramType: " + paramType.toString());
                if (!Util.isPrimitive(paramType)) {
//                    log.error("Non-primitive parameter: " + paramType.toString());
                    SootClass clz = Scene.v().getSootClassUnsafe(paramType.toString());
                    if(clz != null) {
                        PayloadStructure payloadStructure = new PayloadStructure(clz, i);
                        if (entryMapPayloadStructures.containsKey(entryMethod)) {
                            entryMapPayloadStructures.get(entryMethod).add(payloadStructure);
                        } else {
                            List<PayloadStructure> payloadStructures = new ArrayList<>();
                            payloadStructures.add(payloadStructure);
                            entryMapPayloadStructures.put(entryMethod, payloadStructures);
                        }
//                        System.out.println(payloadStructure);
                    }
                }
            }
        }
        log.warn("结构化payload信息提取结束");
        log.warn("为 " + entryMapPayloadStructures.size() + "个entry提取结构信息");

    }

    private static class SingletonHolder {
        private static final SpringEntrySet INSTANCE = new SpringEntrySet();
    }

    public static SpringEntrySet getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
