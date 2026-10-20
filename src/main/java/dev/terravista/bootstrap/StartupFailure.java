package dev.terravista.bootstrap;

/** 携带登录初始化失败的原始异常。 */
public class StartupFailure extends RuntimeException {
    public StartupFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
