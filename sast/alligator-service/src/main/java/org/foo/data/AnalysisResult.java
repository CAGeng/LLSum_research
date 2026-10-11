package org.foo.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AnalysisResult {
    private Long id;
    private Integer vulTotalCount;
    private Integer vulDataReachableCount;
    private String resultData;

}
