package com.campusconnect.common.idempotent;

import com.campusconnect.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 幂等性 AOP 切面
 *
 * 拦截 @Idempotent 注解，通过 Redis SETNX 实现接口级幂等：
 * 1. 从请求 Header 中提取 X-Idempotency-Key
 * 2. 解析 SpEL 表达式生成 Redis Key
 * 3. SETNX 原子操作：成功→执行业务 / 失败→返回"请勿重复提交"
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String IDEMPOTENT_PREFIX = "idempotent:";
    private static final String IDEM_KEY_HEADER = "X-Idempotency-Key";

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.campusconnect.common.idempotent.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 1. 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        // 2. 从 Header 获取幂等 Key
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("[幂等] 非 Web 请求环境，跳过幂等检查");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String idemKey = request.getHeader(IDEM_KEY_HEADER);

        if (idemKey == null || idemKey.isBlank()) {
            log.debug("[幂等] 请求缺少 X-Idempotency-Key Header，跳过幂等检查");
            return joinPoint.proceed();
        }

        // 3. 解析 SpEL 表达式生成最终 Redis Key
        String redisKey;
        try {
            EvaluationContext context = buildContext(joinPoint, method, idemKey);
            String expressionValue = parser.parseExpression(annotation.key()).getValue(context, String.class);
            redisKey = IDEMPOTENT_PREFIX + expressionValue;
        } catch (Exception e) {
            log.warn("[幂等] SpEL 解析失败，使用原始 Header 值: {}", e.getMessage());
            redisKey = IDEMPOTENT_PREFIX + idemKey;
        }

        // 4. Redis SETNX 原子操作
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", Duration.of(annotation.expire(), annotation.timeUnit().toChronoUnit()));

        if (Boolean.TRUE.equals(acquired)) {
            log.debug("[幂等] 首次请求通过，Key={}", redisKey);
            try {
                Object result = joinPoint.proceed();
                // 成功后保留 Key（防止缓存穿透），失败时可考虑删除让用户重试
                return result;
            } catch (Exception e) {
                // 业务异常时删除幂等 Key，允许用户修正后重试
                stringRedisTemplate.delete(redisKey);
                log.debug("[幂等] 业务异常，已释放幂等 Key={}", redisKey);
                throw e;
            }
        } else {
            // 5. 重复提交，直接拒绝
            log.warn("[幂等] 重复提交被拦截，Key={}", redisKey);
            return Result.error(429, annotation.message());
        }
    }

    private EvaluationContext buildContext(ProceedingJoinPoint joinPoint, Method method, String idemKey) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 注入 Header 中的幂等 Key
        context.setVariable("idemKey", idemKey);

        // 注入方法参数（按参数名绑定）
        Object[] args = joinPoint.getArgs();
        String[] paramNames = discoverer.getParameterNames(method);

        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        return context;
    }
}
