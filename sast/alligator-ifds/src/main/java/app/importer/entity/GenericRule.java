package app.importer.entity;

import lombok.*;

/**
 * User: luca
 * Date: 2023/10/19
 * Description:
 */

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenericRule {

    protected String ruleId;

    protected FunctionIdentifier functionIdentifier;

    protected String[] outArguments;

}
