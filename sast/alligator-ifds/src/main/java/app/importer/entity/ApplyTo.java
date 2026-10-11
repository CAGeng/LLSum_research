package app.importer.entity;

import lombok.*;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyTo {

    private boolean tagImplements;
    private boolean tagOverrides;
    private boolean tagExtends;
}
