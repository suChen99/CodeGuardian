package com.codemate.codereview.vo;


import lombok.Data;
/**
 * 所有接口的统一外壳，前端只判断 code == 0。
 */
@Data
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok(T data){
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(0);
        r.setMessage("ok");
        r.setData(data);
        return r;
    }

    public static <T> ApiResponse<T> fail(int code,String message){
        ApiResponse<T> r = new ApiResponse<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
