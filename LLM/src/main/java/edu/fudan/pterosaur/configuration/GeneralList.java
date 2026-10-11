package edu.fudan.pterosaur.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * User: luca
 * Date: 2023/9/13
 * Description:
 */

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "list")
public class GeneralList {
    public List<String> sootInputPaths;

    public List<String> scannedMybatisXmlDirs;

    public List<String> tplPackages;

    public List<String> appPackages;

    public List<String> targetPackages;

}
