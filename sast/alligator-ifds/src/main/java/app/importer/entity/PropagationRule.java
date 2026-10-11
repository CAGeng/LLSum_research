package app.importer.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * User: luca
 * Date: 2023/10/19
 * Description:
 */
@Getter
@Setter
public class PropagationRule extends GenericRule {

    private String[] InArguments;
}
