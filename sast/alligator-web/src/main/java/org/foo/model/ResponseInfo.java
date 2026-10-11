package org.foo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResponseInfo<T> {

    private int code;

    private String msg;

    private T data;

    public ResponseInfo success(T data){
        return ResponseInfo.builder().code(200).msg("successful").data(data).build();
    }

    public ResponseInfo success(){
        return ResponseInfo.builder().code(200).msg("successful").build();
    }
}
