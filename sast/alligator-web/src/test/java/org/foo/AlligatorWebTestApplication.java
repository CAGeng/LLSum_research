package org.foo;

import app.importer.entity.Form;
import app.importer.entity.FunctionIdentifier;
import app.importer.entity.SourceRule;
import org.foo.github.GithubClient;
import org.foo.mapper.SourceRuleMapper;
import org.foo.mapper.TriggerPointMapper;
import org.foo.model.AnalysisRequest;
import org.foo.model.db.DBSourceRule;
import org.foo.model.db.TriggerPoint;
import org.foo.service.GithubService;
import org.foo.service.IFDSService;
import org.foo.service.RuleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@SpringBootTest
public class AlligatorWebTestApplication {

    @Autowired
    IFDSService ifdsService;

    @Autowired
    TriggerPointMapper triggerPointMapper;

    @Autowired
    SourceRuleMapper sourceRuleMapper;

    @Autowired
    RuleService ruleService;

    @Autowired
    GithubClient githubClient;

    @Autowired
    GithubService githubService;

//    @Test
    void testIFDS(){
        String path = "/Users/luca/dev/research/spel/target/classes";
        AnalysisRequest analysisRequest = new AnalysisRequest();
        analysisRequest.setProjectPath(path);
        Object res = ifdsService.analyze(analysisRequest);
        System.out.println(res);
    }

//    @Test
    void testMybatis(){
        List<TriggerPoint> list = triggerPointMapper.findAllTriggerPoint();
        System.out.println(list.size());
    }

//    @Test
    void testGetSourceRule(){
        List<DBSourceRule> list = sourceRuleMapper.findAllSourceRule();
        System.out.println(list.size());
    }

//    @Test
    void testAddSourceRule(){
        SourceRule sourceRule = new SourceRule();
        sourceRule.setRuleId("123");
        sourceRule.setTaintFlags(new String[]{"+XSS", "+WEB"});
        FunctionIdentifier functionIdentifier = new FunctionIdentifier();

        Form namespaceNameForm = new Form();
        namespaceNameForm.setValue("java.util");
        Form classNameForm = new Form();
        classNameForm.setValue("Properties");
        Form functionNameForm = new Form();
        functionNameForm.setValue("run");

        functionIdentifier.setNamespaceName(namespaceNameForm);
        functionIdentifier.setClassName(classNameForm);
        functionIdentifier.setFunctionName(functionNameForm);
        sourceRule.setFunctionIdentifier(functionIdentifier);
        sourceRule.setOutArguments(new String[]{"return"});

        ruleService.addSourceRule(sourceRule);
    }

    @Test
    void testGithubGetRepo() throws IOException {
        String owner = "Lucarun";
        String repoName = "webgoat";
        githubClient.getRepoInfo(owner, repoName);
    }



    @Test
    void testGithub(){
        String jsonStr = "{\"ref\":\"refs/heads/main\",\"before\":\"66171c9a25e81ae614ff78e9655d5160e18580af\",\"after\":\"c430b68cf3336bda7d72d8f50bad72647f749dfb\",\"repository\":{\"id\":719478098,\"node_id\":\"R_kgDOKuJdUg\",\"name\":\"webgoat\",\"full_name\":\"Lucarun/webgoat\",\"private\":false,\"owner\":{\"name\":\"Lucarun\",\"email\":\"zoublue163@163.com\",\"login\":\"Lucarun\",\"id\":26289194,\"node_id\":\"MDQ6VXNlcjI2Mjg5MTk0\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/26289194?v=4\",\"gravatar_id\":\"\",\"url\":\"https://api.github.com/users/Lucarun\",\"html_url\":\"https://github.com/Lucarun\",\"followers_url\":\"https://api.github.com/users/Lucarun/followers\",\"following_url\":\"https://api.github.com/users/Lucarun/following{/other_user}\",\"gists_url\":\"https://api.github.com/users/Lucarun/gists{/gist_id}\",\"starred_url\":\"https://api.github.com/users/Lucarun/starred{/owner}{/repo}\",\"subscriptions_url\":\"https://api.github.com/users/Lucarun/subscriptions\",\"organizations_url\":\"https://api.github.com/users/Lucarun/orgs\",\"repos_url\":\"https://api.github.com/users/Lucarun/repos\",\"events_url\":\"https://api.github.com/users/Lucarun/events{/privacy}\",\"received_events_url\":\"https://api.github.com/users/Lucarun/received_events\",\"type\":\"User\",\"site_admin\":false},\"html_url\":\"https://github.com/Lucarun/webgoat\",\"description\":null,\"fork\":false,\"url\":\"https://github.com/Lucarun/webgoat\",\"forks_url\":\"https://api.github.com/repos/Lucarun/webgoat/forks\",\"keys_url\":\"https://api.github.com/repos/Lucarun/webgoat/keys{/key_id}\",\"collaborators_url\":\"https://api.github.com/repos/Lucarun/webgoat/collaborators{/collaborator}\",\"teams_url\":\"https://api.github.com/repos/Lucarun/webgoat/teams\",\"hooks_url\":\"https://api.github.com/repos/Lucarun/webgoat/hooks\",\"issue_events_url\":\"https://api.github.com/repos/Lucarun/webgoat/issues/events{/number}\",\"events_url\":\"https://api.github.com/repos/Lucarun/webgoat/events\",\"assignees_url\":\"https://api.github.com/repos/Lucarun/webgoat/assignees{/user}\",\"branches_url\":\"https://api.github.com/repos/Lucarun/webgoat/branches{/branch}\",\"tags_url\":\"https://api.github.com/repos/Lucarun/webgoat/tags\",\"blobs_url\":\"https://api.github.com/repos/Lucarun/webgoat/git/blobs{/sha}\",\"git_tags_url\":\"https://api.github.com/repos/Lucarun/webgoat/git/tags{/sha}\",\"git_refs_url\":\"https://api.github.com/repos/Lucarun/webgoat/git/refs{/sha}\",\"trees_url\":\"https://api.github.com/repos/Lucarun/webgoat/git/trees{/sha}\",\"statuses_url\":\"https://api.github.com/repos/Lucarun/webgoat/statuses/{sha}\",\"languages_url\":\"https://api.github.com/repos/Lucarun/webgoat/languages\",\"stargazers_url\":\"https://api.github.com/repos/Lucarun/webgoat/stargazers\",\"contributors_url\":\"https://api.github.com/repos/Lucarun/webgoat/contributors\",\"subscribers_url\":\"https://api.github.com/repos/Lucarun/webgoat/subscribers\",\"subscription_url\":\"https://api.github.com/repos/Lucarun/webgoat/subscription\",\"commits_url\":\"https://api.github.com/repos/Lucarun/webgoat/commits{/sha}\",\"git_commits_url\":\"https://api.github.com/repos/Lucarun/webgoat/git/commits{/sha}\",\"comments_url\":\"https://api.github.com/repos/Lucarun/webgoat/comments{/number}\",\"issue_comment_url\":\"https://api.github.com/repos/Lucarun/webgoat/issues/comments{/number}\",\"contents_url\":\"https://api.github.com/repos/Lucarun/webgoat/contents/{+path}\",\"compare_url\":\"https://api.github.com/repos/Lucarun/webgoat/compare/{base}...{head}\",\"merges_url\":\"https://api.github.com/repos/Lucarun/webgoat/merges\",\"archive_url\":\"https://api.github.com/repos/Lucarun/webgoat/{archive_format}{/ref}\",\"downloads_url\":\"https://api.github.com/repos/Lucarun/webgoat/downloads\",\"issues_url\":\"https://api.github.com/repos/Lucarun/webgoat/issues{/number}\",\"pulls_url\":\"https://api.github.com/repos/Lucarun/webgoat/pulls{/number}\",\"milestones_url\":\"https://api.github.com/repos/Lucarun/webgoat/milestones{/number}\",\"notifications_url\":\"https://api.github.com/repos/Lucarun/webgoat/notifications{?since,all,participating}\",\"labels_url\":\"https://api.github.com/repos/Lucarun/webgoat/labels{/name}\",\"releases_url\":\"https://api.github.com/repos/Lucarun/webgoat/releases{/id}\",\"deployments_url\":\"https://api.github.com/repos/Lucarun/webgoat/deployments\",\"created_at\":1700124911,\"updated_at\":\"2023-11-16T08:57:10Z\",\"pushed_at\":1704168825,\"git_url\":\"git://github.com/Lucarun/webgoat.git\",\"ssh_url\":\"git@github.com:Lucarun/webgoat.git\",\"clone_url\":\"https://github.com/Lucarun/webgoat.git\",\"svn_url\":\"https://github.com/Lucarun/webgoat\",\"homepage\":null,\"size\":18706,\"stargazers_count\":0,\"watchers_count\":0,\"language\":\"JavaScript\",\"has_issues\":true,\"has_projects\":true,\"has_downloads\":true,\"has_wiki\":true,\"has_pages\":false,\"has_discussions\":false,\"forks_count\":0,\"mirror_url\":null,\"archived\":false,\"disabled\":false,\"open_issues_count\":10,\"license\":{\"key\":\"other\",\"name\":\"Other\",\"spdx_id\":\"NOASSERTION\",\"url\":null,\"node_id\":\"MDc6TGljZW5zZTA=\"},\"allow_forking\":true,\"is_template\":false,\"web_commit_signoff_required\":false,\"topics\":[],\"visibility\":\"public\",\"forks\":0,\"open_issues\":10,\"watchers\":0,\"default_branch\":\"main\",\"stargazers\":0,\"master_branch\":\"main\"},\"pusher\":{\"name\":\"Lucarun\",\"email\":\"zoublue163@163.com\"},\"sender\":{\"login\":\"Lucarun\",\"id\":26289194,\"node_id\":\"MDQ6VXNlcjI2Mjg5MTk0\",\"avatar_url\":\"https://avatars.githubusercontent.com/u/26289194?v=4\",\"gravatar_id\":\"\",\"url\":\"https://api.github.com/users/Lucarun\",\"html_url\":\"https://github.com/Lucarun\",\"followers_url\":\"https://api.github.com/users/Lucarun/followers\",\"following_url\":\"https://api.github.com/users/Lucarun/following{/other_user}\",\"gists_url\":\"https://api.github.com/users/Lucarun/gists{/gist_id}\",\"starred_url\":\"https://api.github.com/users/Lucarun/starred{/owner}{/repo}\",\"subscriptions_url\":\"https://api.github.com/users/Lucarun/subscriptions\",\"organizations_url\":\"https://api.github.com/users/Lucarun/orgs\",\"repos_url\":\"https://api.github.com/users/Lucarun/repos\",\"events_url\":\"https://api.github.com/users/Lucarun/events{/privacy}\",\"received_events_url\":\"https://api.github.com/users/Lucarun/received_events\",\"type\":\"User\",\"site_admin\":false},\"created\":false,\"deleted\":false,\"forced\":false,\"base_ref\":null,\"compare\":\"https://github.com/Lucarun/webgoat/compare/66171c9a25e8...c430b68cf333\",\"commits\":[{\"id\":\"c430b68cf3336bda7d72d8f50bad72647f749dfb\",\"tree_id\":\"a44fbc0a72a7d28aea7658558ea0044a3261b88f\",\"distinct\":true,\"message\":\"Update pom.xml\",\"timestamp\":\"2024-01-02T12:13:45+08:00\",\"url\":\"https://github.com/Lucarun/webgoat/commit/c430b68cf3336bda7d72d8f50bad72647f749dfb\",\"author\":{\"name\":\"Luca\",\"email\":\"zoublue163@163.com\",\"username\":\"Lucarun\"},\"committer\":{\"name\":\"GitHub\",\"email\":\"noreply@github.com\",\"username\":\"web-flow\"},\"added\":[],\"removed\":[],\"modified\":[\"pom.xml\"]}],\"head_commit\":{\"id\":\"c430b68cf3336bda7d72d8f50bad72647f749dfb\",\"tree_id\":\"a44fbc0a72a7d28aea7658558ea0044a3261b88f\",\"distinct\":true,\"message\":\"Update pom.xml\",\"timestamp\":\"2024-01-02T12:13:45+08:00\",\"url\":\"https://github.com/Lucarun/webgoat/commit/c430b68cf3336bda7d72d8f50bad72647f749dfb\",\"author\":{\"name\":\"Luca\",\"email\":\"zoublue163@163.com\",\"username\":\"Lucarun\"},\"committer\":{\"name\":\"GitHub\",\"email\":\"noreply@github.com\",\"username\":\"web-flow\"},\"added\":[],\"removed\":[],\"modified\":[\"pom.xml\"]}}";
        githubService.analysisGithubEvent(jsonStr);
    }



}
