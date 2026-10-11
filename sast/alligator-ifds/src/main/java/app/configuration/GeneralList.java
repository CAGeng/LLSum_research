package app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * User: luca
 * Date: 2023/9/13
 * Description:
 */

@Component
@ConfigurationProperties(prefix = "list")
public class GeneralList {
    public List<String> sootInputPaths;

    public List<String> scannedMybatisXmlDirs;
    public List<String> additionalSinks;

    public void setAdditionalSinks(List<String> additionalSinks) {
        this.additionalSinks = additionalSinks;
    }

    public void setSootInputPaths(List<String> sootInputPaths) {
        this.sootInputPaths = sootInputPaths;
    }

    public void setScannedMybatisXmlDirs(List<String> scannedMybatisXmlDirs) {
        this.scannedMybatisXmlDirs = scannedMybatisXmlDirs;
    }
}
