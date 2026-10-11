package basic.util.StaticAnalyzeUtils;

import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.*;

import java.util.*;

public class AnnotationUtil {

    public static HashSet<AnnotationTag> getClassAnnotations(SootClass sootClass){
        HashSet<AnnotationTag> ret = new HashSet<>();
        if(sootClass.getTags().size() == 0){
            return ret;
        }

        VisibilityAnnotationTag visibilityAnnotationTag = (VisibilityAnnotationTag) sootClass.getTag("VisibilityAnnotationTag");
        if (visibilityAnnotationTag !=null){
            ret.addAll(visibilityAnnotationTag.getAnnotations());
        }
        return ret;
    }

    public static List<String> getAnnotationType(SootClass sootClass){
        List<String> annotiationTypeList = new ArrayList<>();
        List<Tag> tagList = sootClass.getTags();
        for (Tag tag : tagList) {
            if (tag instanceof VisibilityAnnotationTag) {
                ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                for (AnnotationTag annotationTag : annotationTags) {
                    String annotationType = annotationTag.getType();
                    annotiationTypeList.add(annotationType);
                }
            }
        }
        return annotiationTypeList;
    }

    public static List<String> getAnnotationType(SootMethod sootMethod){
        List<String> annotiationTypeList = new ArrayList<>();
        List<Tag> tagList = sootMethod.getTags();
        for (Tag tag : tagList) {
            if (tag instanceof VisibilityAnnotationTag) {
                ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                for (AnnotationTag annotationTag : annotationTags) {
                    String annotationType = annotationTag.getType();
                    annotiationTypeList.add(annotationType);
                }
            }
        }
        return annotiationTypeList;
    }

    public static HashMap<String, List<String>> getAnnotationTypeAndValues(SootClass sootClass) {

        HashMap<String, List<String>> annotationTypeMapValues = new HashMap<>();
        HashSet<AnnotationTag> classAnnotations = ClassUtils.getClassAnnotations(sootClass);

        // url path defined in class anno
        for(AnnotationTag annotationTag : classAnnotations) {
            Collection<AnnotationElem> elems = annotationTag.getElems();

            for(AnnotationElem elem : elems) {
                String value = null;
                if(elem instanceof AnnotationArrayElem) {
                    for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()) {
                        if(annotationElem instanceof AnnotationStringElem) {
                            value = ((AnnotationStringElem) annotationElem).getValue();
                        }
                    }
                } else if (elem instanceof AnnotationStringElem) {
                    value = ((AnnotationStringElem) elem).getValue();
                }

                if(value != null) {
                    String annotationType = annotationTag.getType();
                    if(!annotationTypeMapValues.containsKey(annotationType)) {
                        annotationTypeMapValues.put(annotationType, new ArrayList<>());
                    }
                    annotationTypeMapValues.get(annotationType).add(value);
                }
            }
        }
        return annotationTypeMapValues;
    }

    public static HashMap<String, List<String>> getAnnotationTypeAndValues(SootMethod sootMethod) {

        HashMap<String, List<String>> annotationTypeMapValues = new HashMap<>();

        List<Tag> tagList = sootMethod.getTags();
        for (Tag tag : tagList) {
            if (tag instanceof VisibilityAnnotationTag) {
                ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                for (AnnotationTag annotationTag : annotationTags) {
                    String value = null;
                    String annotationType = annotationTag.getType();
                    Collection<AnnotationElem> elems = annotationTag.getElems();
                    for(AnnotationElem elem : elems) {
                        if(elem instanceof AnnotationArrayElem) {
                            for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()){
                                if(annotationElem instanceof AnnotationStringElem) {
                                    value = ((AnnotationStringElem) annotationElem).getValue();
                                }
                            }
                        }
                    }
                    if(value != null) {
                        if(!annotationTypeMapValues.containsKey(annotationType)) {
                            annotationTypeMapValues.put(annotationType, new ArrayList<>());
                        }
                        annotationTypeMapValues.get(annotationType).add(value);
                    }
                }
            }
        }
        return annotationTypeMapValues;
    }

}
