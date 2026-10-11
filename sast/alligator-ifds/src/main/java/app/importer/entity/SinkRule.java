package app.importer.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Setter
@Getter
public class SinkRule {

    private String ruleID;

    private Sink sink;

    private FunctionIdentifier functionIdentifier;

    private String vulCategory;
}
