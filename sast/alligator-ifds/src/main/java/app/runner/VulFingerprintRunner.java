package app.runner;

import app.configuration.GeneralConfig;
import detect.VulFingerprintDetector;
import detect.vulexp.datastruct.RiskyFuncInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Component;

/**
 * User: luca
 * Date: 2023/9/21
 * Description:
 */
@ConditionalOnNotWebApplication
//@Component
@Slf4j
public class VulFingerprintRunner implements CommandLineRunner {

    @Autowired
    VulFingerprintDetector vulFingerPrintDetector;

    @Autowired
    GeneralConfig generalConfig;



    @Override
    public void run(String... args){

        log.info("VulFingerPrintDetector is running");
//        RiskyFuncInfo riskyFuncInfo = new RiskyFuncInfo("com.alibaba.fastjson.JSON", "parse", null, null);
//        vulFingerPrintDetector.riskyFuncInfos.add(riskyFuncInfo);
//        RiskyFuncInfo riskyFuncInfo = new RiskyFuncInfo("com.alibaba.druid.filter.FilterChainImpl", "connection_connect", null, null);
//        vulFingerPrintDetector.riskyFuncInfos.add(riskyFuncInfo);

        for(String rf : generalConfig.getUpstreamConfig().getRiskyfuncs()) {
            String[] split = rf.split(":");
            vulFingerPrintDetector.riskyFuncInfos.add(new RiskyFuncInfo(split[0], split[1], null, null));
        }

        vulFingerPrintDetector.detect();
    }
}
