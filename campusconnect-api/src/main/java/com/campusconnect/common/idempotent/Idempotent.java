package com.campusconnect.common.idempotent;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 接口幂等性注解
 *
 * 原理：请求进入时，从 Header 中获取 X-Idempotency-Key，
 * Redis SETNX 原子判断是否已处理，防止重复提交。
 *
 * 使用方式：
 * <pre>{@code
 *   @Idempotent(key = "#userId + ':' + #request.title", expire = 5, timeUnit = TimeUnit.SECONDS)
 *   public Result<?> createPost(@RequestHeader("X-Idempotency-Key") String idemKey, ...)
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等 Key 的 SpEL 表达式
     * 支持从方法参数和 Header 中取值
     */
    String key() default "#idemKey";

    /**
     * 过期时间，默认 5 秒
     * 超过此时间后允许重新提交（防止网络超时导致永远无法重试）
     */
    long expire() default 5;

    /**
     * 时间单位，默认秒
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 幂等冲突时返回的错误信息
     */
    String message() default "操作正在处理中，请勿重复提交";
}
