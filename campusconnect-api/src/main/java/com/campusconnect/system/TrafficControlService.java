package com.campusconnect.system;

import com.campusconnect.common.dynamic.DccValue;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TrafficControlService {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否开启流控：1 开启，0 关闭
     */
    @DccValue("traffic.enabled:1")
    private Integer trafficEnabled;

    /**
     * 每个接口每秒最大访问次数
     */
    @DccValue("traffic.qps:30")
    private Integer trafficQps;

    /**
     * 是否开启全站降级：1 开启，0 关闭
     */
    @DccValue("traffic.degrade:0")
    private Integer trafficDegrade;

    private static final String TRAFFIC_KEY_PREFIX = "campus:traffic:sliding:";

    public boolean needReject(String uri) {
        if (trafficEnabled == null || trafficEnabled == 0) {
            return false;
        }

        if (trafficDegrade != null && trafficDegrade == 1) {
            return true;
        }

        int qps = trafficQps == null ? 30 : trafficQps;
        long now = System.currentTimeMillis();
        long windowStart = now - 1000; // 过去 1 秒的滑动窗口

        String key = TRAFFIC_KEY_PREFIX + uri;

        // 1. 删除过期记录（窗口外的数据）
        stringRedisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        // 2. 统计窗口内的请求数
        Long count = stringRedisTemplate.opsForZSet().count(key, windowStart, now);

        if (count != null && count >= qps) {
            return true; // 触发限流
        }

        // 3. 记录本次请求（score=时间戳, member=时间戳+随机数区分同一毫秒并发）
        stringRedisTemplate.opsForZSet().add(key, now + "_" + System.nanoTime(), now);
        stringRedisTemplate.expire(key, Duration.ofSeconds(3)); // 3秒后整key过期清理

        return false;
    }
}
