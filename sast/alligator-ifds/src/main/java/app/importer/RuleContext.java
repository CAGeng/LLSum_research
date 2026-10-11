package app.importer;

import app.importer.entity.PropagationRule;
import app.importer.entity.SinkRule;
import app.importer.entity.SourceRule;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;

/**
 * User: luca
 * Date: 2023/10/10
 * Description:
 */

@Getter
@Setter
@Component
public class RuleContext {

    private List<SourceRule> sourceRules = new LinkedList();

    private List<SinkRule> sinkRules = new LinkedList();

    private List<PropagationRule> propagationRules = new LinkedList();

}
