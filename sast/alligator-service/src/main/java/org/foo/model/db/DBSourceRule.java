package org.foo.model.db;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User: luca
 * Date: 2023/11/7
 * Description:
 */

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DBSourceRule {

    private String ruleID;

    private String taintFlags;

    private String namespaceName;

    private String className;

    private String functionName;

    private boolean tagImplements;

    private boolean tagOverrides;

    private boolean tagExtends;

    private String outArguments;

}
