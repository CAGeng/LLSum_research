package TranModel.datastruct;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class SrcMessage {
    public int entryParamId;
    public boolean isStructure;

    @JsonIgnore
    public PayloadStructure payloadStructure;
    public String accessPath;

    public SrcMessage(int entryParamId, boolean isStructure, PayloadStructure dataStructure, String accessPath) {
        this.entryParamId = entryParamId;
        this.isStructure = isStructure;
        this.payloadStructure = dataStructure;
        this.accessPath = accessPath;
    }
}
