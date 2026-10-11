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
public class Commit {

    private String id;

    private String message;

    private String url;

}
