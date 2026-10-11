package app.configuration;

import basic.common.SootInit;
import basic.reflect.LoadClass;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * User: luca
 * Date: 2023/10/6
 * Description:
 */

@Component
public class SootConfig {

    @Autowired
    GeneralConfig generalConfig;

    @PostConstruct
    @ConditionalOnNotWebApplication
    public void init(){
        SootInit.setSoot_inputClass(generalConfig.sootInputPaths);
        LoadClass.additionalPath = generalConfig.sootAdditionalPath;
    }

    public static void initSoot(String sootInputPaths){
        SootInit.setSoot_inputClass(sootInputPaths);
    }
}
