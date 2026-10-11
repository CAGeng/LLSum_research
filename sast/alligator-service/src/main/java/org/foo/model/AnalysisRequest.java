package org.foo.model;


import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ApiModel("Analysis Params")
public class AnalysisRequest {

    /**
     * folder1,folder2,folder3
     */
    @ApiModelProperty("projectPath")
    private String projectPath;

    private String vulType;

}
