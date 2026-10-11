package detect.knowledge.entry.discovery;

import app.context.SpringContextUtils;
import detect.knowledge.entry.ArgTypeGuidedEntrySet;
import detect.knowledge.entry.HasParameterEntrySet;
import soot.SootClass;

import java.util.HashSet;

public class ArgDiscoverEntrySet extends ArgTypeGuidedEntrySet {

    public ArgDiscoverEntrySet() {
        CommunicateArgDiscovery communicateArgDiscovery = SpringContextUtils.getBean(CommunicateArgDiscovery.class);
        communicateArgDiscovery.run();
        HashSet<SootClass> extendedCommunicateClasses = communicateArgDiscovery.extendedCommunicateClasses;

        for(SootClass sootClass : extendedCommunicateClasses) {
            argTypes.add(sootClass.getName());
        }
    }

}
