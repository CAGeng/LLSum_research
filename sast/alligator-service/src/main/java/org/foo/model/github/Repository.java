package org.foo.model.github;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

/**
 * User: luca
 * Date: 2024/1/2
 * Description:
 */

@Getter
@Setter
public class Repository {

    private String name;

    @SerializedName("full_name")
    private String fullName;

    private String url;

}
