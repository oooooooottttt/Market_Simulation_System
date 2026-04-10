package com.project___10.demo.common;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;    // 状态码 (比如 200 成功, 500 失败)
    private String msg;      // 提示消息
    private T data;          // 实际的数据

    // 快捷成功的静态方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    // 🚀 这就是你 Controller 里报错缺少的那个方法！
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(500); // 这里的 500 是通用的错误码
        result.setMsg(msg);
        return result;
    }
}