package io.lbynb.islandstorm.web.api;

/** API 处理异常：携带 HTTP 状态码与错误信息，由 ApiRouter 统一转成 JSON 错误响应。 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException badRequest(String msg) {
        return new ApiException(400, msg);
    }

    public static ApiException forbidden(String msg) {
        return new ApiException(403, msg);
    }

    public static ApiException notFound(String msg) {
        return new ApiException(404, msg);
    }
}
