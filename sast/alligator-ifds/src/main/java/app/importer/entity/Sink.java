package app.importer.entity;

import lombok.Getter;
import lombok.Setter;
import org.dom4j.Element;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Getter
@Setter
public class Sink {

    private String[] inArguments;

    private Element conditional;

}
