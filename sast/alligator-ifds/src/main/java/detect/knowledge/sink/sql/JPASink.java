package detect.knowledge.sink.sql;

import basic.util.StaticAnalyzeUtils.ClassUtils;
import basic.util.StaticAnalyzeUtils.MethodUtil;
import detect.knowledge.sink.HasParamMethodSinks;
import detect.knowledge.sink.MethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.tagkit.AnnotationArrayElem;
import soot.tagkit.AnnotationElem;
import soot.tagkit.AnnotationStringElem;
import soot.tagkit.AnnotationTag;

import java.util.*;

@Slf4j
//@Component
public class JPASink extends HasParamMethodSinks implements MethodSinks {

    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();
        SootClass jpaRepoClass = Scene.v().getSootClassUnsafe("org.springframework.data.jpa.repository.JpaRepository");
        if(jpaRepoClass != null) {
            for(SootClass sootClass : ClassUtils.getAllSubs(jpaRepoClass)) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    log.warn("JPA method: " + sootMethod.getSignature());
                    if(judgeMethodRisky(sootMethod)) {
                        sinkMethods.add(sootMethod);
                        List<Integer> params = new LinkedList<>();
                        for(int i = 0; i < sootMethod.getParameterCount(); i++) {
                            params.add(i);
                        }
                        sinkMethodsParams.put(sootMethod, params);
                        log.error("JPA sink method: " + sootMethod.getSignature());
                    }
                }
            }
        }
    }

    public boolean judgeMethodRisky(SootMethod sootMethod) {
        HashSet<AnnotationTag> methodAnnotations = MethodUtil.getMethodAnnotations(sootMethod);

        if(!methodAnnotations.isEmpty()) {
            for (AnnotationTag annotationTag : methodAnnotations) {
                String anno = annotationTag.getType();
                if (anno.equals("Lorg/springframework/data/jpa/repository/Query;")) {
                    String sql;
                    // 有方法注解的，认为是通过注解配置的sql语句
                    Collection<AnnotationElem> elems = annotationTag.getElems();
                    StringBuilder sb = new StringBuilder();
                    for(AnnotationElem elem : elems) {
                        if(elem instanceof AnnotationArrayElem) {
                            for (AnnotationElem annotationElem : ((AnnotationArrayElem) elem).getValues()){
                                if(annotationElem instanceof AnnotationStringElem) {
                                    sb.append(((AnnotationStringElem) annotationElem).getValue()).append(" ");
                                }
                            }
                        } else if (elem instanceof AnnotationStringElem) {
                            sb.append(((AnnotationStringElem) elem).getValue()).append(" ");
                        }
                    }
                    sql = sb.toString().trim();
                    if(sql.contains("\'")){
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String getTypeName() {
        return "sink_type_sql";
    }
}
