package com.project.rate_limiting.limiter;

import com.project.rate_limiting.config.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class SlidingWindowRateLimiter {

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String BAN_KEY_PREFIX = "rate-limit:ban:";


    public boolean allowRequest(String clientId, String userTier) {
        if(isUserBanned(clientId)){
            return false;
        }

        int requestLimit;
        long windowSizeMillis;

        if ("premium".equalsIgnoreCase(userTier)) {
            requestLimit = rateLimitProperties.getPremium().getLimit();
            windowSizeMillis = rateLimitProperties.getPremium().getWindow();
        } else {
            requestLimit = rateLimitProperties.getFree().getLimit();
            windowSizeMillis = rateLimitProperties.getFree().getWindow();
        }
        String redisKey = "sliding-window:" + clientId;
        long currentTime = System.currentTimeMillis();

        long windowStart = currentTime - windowSizeMillis;

        redisTemplate.opsForZSet().removeRangeByScore(redisKey, 0, windowStart);

        Long requestCount = redisTemplate.opsForZSet().zCard(redisKey);
        System.out.println(requestCount);

        if (requestCount != null && requestCount < requestLimit) {
            redisTemplate.opsForZSet().add(redisKey, String.valueOf(currentTime), currentTime);
            redisTemplate.expire(redisKey, windowStart, TimeUnit.MILLISECONDS);
            return true;
        }
        return false;
    }

    public void resetUserLimit(String clientId) {
        String redisKey = "sliding-window:" + clientId;
        redisTemplate.delete(redisKey);
    }

    public void banUser(String clientId) {
        String banKey = BAN_KEY_PREFIX + clientId;
        redisTemplate.opsForValue().set(banKey, "true");
    }

    public void unbanUser(String clientId) {
        String banKey = BAN_KEY_PREFIX + clientId;
        redisTemplate.delete(banKey);
    }

    public boolean isUserBanned(String clientId) {
        String banKey = BAN_KEY_PREFIX + clientId;
        return redisTemplate.opsForValue().get(banKey) != null;
    }

    public Map<String, Object> getUserRateLimitStatus(String clientId, String userTier) {
        Map<String, Object> status = new HashMap<>();

        int requestLimit;
        long windowSizeMillis;

        if ("premium".equalsIgnoreCase(userTier)) {
            requestLimit = rateLimitProperties.getPremium().getLimit();
            windowSizeMillis = rateLimitProperties.getPremium().getWindow();
        } else {
            requestLimit = rateLimitProperties.getFree().getLimit();
            windowSizeMillis = rateLimitProperties.getFree().getWindow();
        }

        status.put("requestLimit", requestLimit);
        status.put("windowSizeMillis", windowSizeMillis);
        status.put("isBanned", isUserBanned(clientId));
        return status;
    }
}
