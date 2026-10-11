package detect.knowledge.sink.sql;

import detect.knowledge.sink.HasParamMethodSinks;
import detect.knowledge.sink.MethodSinks;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;

@Slf4j
//@Component
public class MethodSinksSql extends HasParamMethodSinks implements MethodSinks {

    @Override
    public void initSinkMethods() {
        sinkMethods = new LinkedList<>();
        sinkMethodsParams = new HashMap<>();
        conditional = new HashMap<>();
        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("org.springframework.jdbc.core.JdbcTemplate")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    String sootMethodName = sootMethod.getName();
                    if(sootMethodName.equals("query") || sootMethodName.equals("execute")) {
                        sinkMethods.add(sootMethod);
                        // 第一个参数是sql语句
                        sinkMethodsParams.put(sootMethod, Collections.singletonList(0));
                    }
                }
            }
        }

        for (SootClass sootClass : Scene.v().getClasses()) {
            if (sootClass.getName().equals("java.sql.Connection")) {
                for(SootMethod sootMethod : sootClass.getMethods()) {
                    if(sootMethod.getName().equals("prepareStatement")) {
                        addMethodAndParam(sootMethod.getSignature(), 0);
                    }
                }
            }

        }

        addMethodAndParam("<java.sql.Statement: java.sql.ResultSet executeQuery(java.lang.String)>", 0);

        log.warn("[Sinks] dbQueryMethods: " + sinkMethods.size());
    }

    @Override
    public String getTypeName() {
        return "sink_type_sql";
    }
}
