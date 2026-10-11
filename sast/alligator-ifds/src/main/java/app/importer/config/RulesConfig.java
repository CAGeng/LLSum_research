package app.importer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Component
public class RulesConfig {

    @Value("${rules.entrydetect.com.path}")
    public String entryCommunicationPath;

    @Value("${rules.entrydetect.anno.path}")
    public String entryAnnoPath;

    @Value("${rules.core.path}")
    public String rulesCorePath;

    @Value("${rules.added.path}")
    public String rulesAddedPath;

    @Value("${rules.extend.path}")
    public String rulesExtendPath;

    @Value("${rules.core.thirdPartyPath}")
    public String rulesCoreThirdPartyPath;

    @Value("${rules.added.thirdPartyPath}")
    public String rulesAddedThirdPartyPath;

    @Value("${rules.extend.thirdPartyPath}")
    public String rulesExtendThirdPartyPath;

    @Value("${rules.entry.path}")
    public String rulesEntryPath;

    @Value("${rules.entry.switcher}")
    public boolean rulesEntrySwitcher;

    @Value("${rules.core.pathSwitcher}")
    public boolean rulesCorePathSwitcher;

    @Value("${rules.added.pathSwitcher}")
    public boolean rulesAddedPathSwitcher;

    @Value("${rules.extend.pathSwitcher}")
    public boolean rulesExtendPathSwitcher;

    @Value("${rules.core.thirdPartyPathSwitcher}")
    public boolean rulesCoreThirdPartyPathSwitcher;

    @Value("${rules.extend.thirdPartyPathSwitcher}")
    public boolean rulesExtendThirdPartyPathSwitcher;

    @Value("${rules.added.thirdPartyPathSwitcher}")
    public boolean rulesAddedThirdPartyPathSwitcher;

    @Value("${rules.conditional}")
    public boolean rulesConditionalSwitcher;

    @Value("${rules.taintFlag}")
    public boolean rulesTaintFlagSwitcher;

}
