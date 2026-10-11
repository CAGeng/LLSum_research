package detect.knowledge.entry.jetty;

import detect.knowledge.entry.ArgTypeGuidedEntrySet;

public class RequestTypeEntrySet extends ArgTypeGuidedEntrySet {
        public RequestTypeEntrySet() {
            argTypes.add("org.eclipse.jetty.server.Request");
            argTypes.add("jakarta.servlet.http.HttpServletRequest");
            argTypes.add("jakarta.servlet.ServletRequest");
        }
}
