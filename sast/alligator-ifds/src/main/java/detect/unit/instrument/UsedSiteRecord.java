package detect.unit.instrument;

import TranModel.datastruct.SrcMessage;

import java.util.HashSet;

public class UsedSiteRecord {
    public String inClassName;
    public Integer site;

    public HashSet<SrcMessage> srcMessages = new HashSet<>();
}
