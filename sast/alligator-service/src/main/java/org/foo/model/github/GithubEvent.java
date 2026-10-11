package org.foo.model.github;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * User: luca
 * Date: 2024/1/2
 * Description:
 */

@Getter
@Setter
public class GithubEvent {

    private String ref;

    private String before;

    private String after;

    private List<Commit> commits;

    private Repository repository;
}
