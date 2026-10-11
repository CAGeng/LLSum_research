package org.foo.service.impl;

import app.utils.UUIDUtil;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.foo.github.GithubClient;
import org.foo.model.AnalysisRequest;
import org.foo.model.github.Commit;
import org.foo.model.github.GithubEvent;
import org.foo.model.github.Repository;
import org.foo.service.GithubService;
import org.foo.service.IFDSService;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: luca
 * Date: 2023/12/27
 * Description:
 */

@Service
@Slf4j
public class GithubServiceImpl implements GithubService<String> {

    private static String PATH = "/tmp/github";

    @Autowired
    GithubClient githubClient;

    @Autowired
    IFDSService ifdsService;

    @Override
    public void addCommentsToCommit(String s) {

    }

    @Override
    public void addCommentsToPR(String s) {

    }

    @Override
    public void analysisGithubEvent(String jsonStr){
        GithubEvent githubEvent = getGithubEvent(jsonStr);
        Repository repo = githubEvent.getRepository();

        String[] arr = repo.getFullName().split("/");
        String owner = arr[0];
        String repoName = arr[1];

        String[] refArr = githubEvent.getRef().split("/");
        String branch = refArr[2];

        String localPath = PATH + "/" + repoName + UUIDUtil.generateRandomNumber(10);

        List<Commit> commitList = githubEvent.getCommits();
        if (commitList != null && commitList.size() > 0){
            Commit commit = commitList.get(0);
            String sha = commit.getId();

            // clone the project
            log.info("start to clone project");
            GHRepository repository = githubClient.getRepo(owner,repoName);
            String cloneUrl = repository.getSshUrl();
            log.info("cloneUrl is : " + cloneUrl);
            ProcessBuilder processBuilder = new ProcessBuilder("git", "clone", cloneUrl, localPath);
            try {
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("Git clone successful");
                    ProcessBuilder checkoutProcessBuilder = new ProcessBuilder("git", "checkout", branch);
                    checkoutProcessBuilder.directory(new File(localPath));
                    Process checkoutProcess = checkoutProcessBuilder.start();
                    int checkoutExitCode = checkoutProcess.waitFor();
                    if (checkoutExitCode == 0) {
                        log.info("Checked out to branch: " + branch);
                    } else {
                        log.error("Git checkout failed with exit code: " + checkoutExitCode);
                    }
                } else {
                    log.error("Git clone failed");
                    return;
                }
            } catch (Exception e) {
                log.error("Git clone failed");
                log.error(e.getMessage());
                return;
            }


            // maven build
            log.info("start to maven build");
            String pomPath = localPath + "/pom.xml";
            log.info("pomPath is : " + pomPath);
            processBuilder = new ProcessBuilder("mvn", "clean", "install", "-Dmaven.test.skip=true", "-f "+ pomPath);
            try {
                Process process = processBuilder.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    log.info("Maven build successful");
                } else {
                    log.error("Maven build failed");
                    return;
                }
            } catch (Exception e) {
                log.error("Maven build failed");
                log.error(e.getMessage());
                return;
            }

            // analysis
            log.info("start to analysis project");
            AnalysisRequest analysisRequest = new AnalysisRequest();
            String projectPath = localPath + "/target/classes";
            analysisRequest.setProjectPath(projectPath);
            String comment = ifdsService.analyze(analysisRequest);

            try {
                githubClient.addCommentToCommit(owner, repoName, sha, comment);
            } catch (IOException e) {
                log.error(e.getMessage());
            }

            // todo. empty the repo

        }
    }

    private GithubEvent getGithubEvent(String jsonStr) {
        Gson gson = new Gson();
        GithubEvent githubEvent = gson.fromJson(jsonStr, GithubEvent.class);
        return githubEvent;
    }
}
