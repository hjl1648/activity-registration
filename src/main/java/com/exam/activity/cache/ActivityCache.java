package com.exam.activity.cache;

import com.exam.activity.domain.Activity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * 仅缓存活动基础信息（标题、状态、总名额），剩余名额始终以 MySQL 为准。
 */
@Component
public class ActivityCache {

    private static final Logger log = LoggerFactory.getLogger(ActivityCache.class);
    private static final String KEY_PREFIX = "activity:base:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public ActivityCache(StringRedisTemplate redis,
                         ObjectMapper objectMapper,
                         @Value("${app.cache.activity-ttl-seconds:60}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<CachedActivityBase> get(long activityId) {
        String key = KEY_PREFIX + activityId;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                log.info("Redis MISS key={}", key);
                return Optional.empty();
            }
            log.info("Redis HIT key={}", key);
            return Optional.of(objectMapper.readValue(json, CachedActivityBase.class));
        } catch (Exception e) {
            log.warn("Redis read failed for key={}, fallback to DB: {}", key, e.toString());
            return Optional.empty();
        }
    }

    public void put(Activity activity) {
        String key = KEY_PREFIX + activity.getId();
        CachedActivityBase base = new CachedActivityBase(
                activity.getId(), activity.getTitle(), activity.getStatus(), activity.getTotalQuota());
        try {
            String json = objectMapper.writeValueAsString(base);
            redis.opsForValue().set(key, json, ttl);
            log.info("Redis WRITE key={} ttlSeconds={}", key, ttl.getSeconds());
        } catch (JsonProcessingException e) {
            log.warn("Redis serialize failed for key={}: {}", key, e.toString());
        } catch (Exception e) {
            log.warn("Redis write failed for key={}: {}", key, e.toString());
        }
    }

    public static class CachedActivityBase {
        private Long id;
        private String title;
        private String status;
        private Integer totalQuota;

        public CachedActivityBase() {}

        public CachedActivityBase(Long id, String title, String status, Integer totalQuota) {
            this.id = id;
            this.title = title;
            this.status = status;
            this.totalQuota = totalQuota;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getTotalQuota() { return totalQuota; }
        public void setTotalQuota(Integer totalQuota) { this.totalQuota = totalQuota; }
    }
}
