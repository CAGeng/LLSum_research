package app.importer.entity;

import lombok.*;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SourceRule extends GenericRule{

    private String[] taintFlags;

    private String language;

}
