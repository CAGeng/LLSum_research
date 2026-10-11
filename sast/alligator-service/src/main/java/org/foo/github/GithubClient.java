package org.foo.github;

import lombok.extern.slf4j.Slf4j;
import org.kohsuke.github.*;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

/**
 * User: luca
 * Date: 2023/12/27
 * Description:
 */



@Component
@Slf4j
public class GithubClient {

    private static GitHub github;

    private static String GITHUB_TOKEN = "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";

    @PostConstruct
    void init() throws IOException {
        github = new GitHubBuilder().withOAuthToken(GITHUB_TOKEN).build();
    }


    public void getRepoInfo(String owner, String repoName) throws IOException {
        GHRepository repository = github.getRepository(owner + "/" + repoName);
        log.info("Repository Name: " + repository.getName());
        log.info("Description: " + repository.getDescription());
        log.info("Default Branch: " + repository.getDefaultBranch());
    }



    public void getCommits(String owner, String repoName) throws IOException {
        GHRepository repository = github.getRepository(owner + "/" + repoName);
        PagedIterable<GHCommit> commits = repository.listCommits();
        log.info("Commits in " + repoName + ":");
        for (GHCommit commit : commits) {
            log.info("SHA: " + commit.getSHA1());
            log.info("Author: " + commit.getAuthor().getName());
            log.info("Date: " + commit.getCommitDate());
            log.info("Message: " + commit.getCommitShortInfo().getMessage());
        }
    }

    public void addCommentToCommit(String owner, String repoName, String commitSha, String commentBody) throws IOException {
        GHRepository repository = github.getRepository(owner + "/" + repoName);
        GHCommit commit = repository.getCommit(commitSha);
        GHCommitComment comment = commit.createComment(commentBody);
        log.info("Comment added successfully:");
        log.info("Comment ID: " + comment.getId());
        log.info("Comment Body: " + comment.getBody());
        log.info("Comment URL: " + comment.getHtmlUrl());
    }

    public GHRepository getRepo(String owner, String repoName){
        try {
            return github.getRepository(owner + "/" + repoName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
