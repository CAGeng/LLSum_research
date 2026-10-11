package detect.knowledge.sink;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * User: luca
 * Date: 2023/10/11
 * Description:
 */

@Component
public class MethodSinksCollection {

    @Autowired
    Map<String, MethodSinks> methodSinksMap;

    public Map<String, MethodSinks> getMethodSinksMap(){
        return methodSinksMap;
    }

    public void addMethodSinks(String key, MethodSinks methodSinks){
        if (methodSinksMap.containsKey(key)){
            return;
        }
        methodSinksMap.put(key, methodSinks);
    }

}
