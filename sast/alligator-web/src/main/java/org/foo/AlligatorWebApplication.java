package org.foo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@ComponentScan(basePackages={"detect","app","TranModel","org.foo"})
@SpringBootApplication
@EnableWebMvc
public class AlligatorWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlligatorWebApplication.class, args);
    }
}
