package app.runner;

import detect.Summarizer;
import detect.VulDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnNotWebApplication;
import org.springframework.stereotype.Component;

@ConditionalOnNotWebApplication
//@Component
@Slf4j
public class SummaryRunner implements CommandLineRunner {
    @Autowired
    Summarizer summarizer;


    @Override
    public void run(String... args){

        log.info("summarizer is running");
        summarizer.detect();
    }
}
