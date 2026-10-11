package app.runner;

import detect.VulDetector;
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
@Component
@Slf4j
public class VulDetectRunner implements CommandLineRunner {

    @Autowired
    VulDetector vulDetector;



    @Override
    public void run(String... args){

        log.info("VulDetectRunner is running");
        vulDetector.detect();
    }
}
