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
public class FunctionIdentifier {
    /**
     *
     */
    private Form namespaceName;
    /**
     *
     */
    private Form className;
    /**
     *
     */
    private Form functionName;
    /**
     *
     */
    private ApplyTo applyTo;
    /**
     *
     */
    private String[] parameters;
    /**
     *
     */
    private String classLabel;
    /**
     *
     */
    private String functionLabel;
    /**
     *
     */
    private String namespaceLabel;
}
