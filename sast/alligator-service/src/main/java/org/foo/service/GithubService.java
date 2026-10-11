package org.foo.service;

import org.foo.model.github.GithubEvent;

import java.io.IOException;

/**
 * User: luca
 * Date: 2023/12/27
 * Description:
 */
public interface GithubService<T> {

    void addCommentsToCommit(T t);

    void addCommentsToPR(T t);

    void analysisGithubEvent(String jsonStr);

}
