package com.project.rate_limiting.limiter;

import com.project.rate_limiting.config.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class FixedWindowRateLimiter{

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String BAN_KEY_PREFIX = "rate-limit:ban:";


    public boolean allowRequest(String clientId, String userTier) {

        if (isUserBanned(clientId)) {
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


        String redisKey = "rate-limit:" + clientId;
        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        System.out.println(currentCount);

        // If it's the first request, set the expiration time for the counter
        if (currentCount == 1) {
            redisTemplate.expire(redisKey, Duration.ofMillis(windowSizeMillis));
        }

        // Check if the user has exceeded the limit
        return currentCount <= requestLimit;
    }

    public void resetUserLimit(String clientId) {
        String redisKey = "rate-limit:" + clientId;
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
        String redisKey = "rate-limit:" + clientId;

        int requestLimit;
        long windowSizeMillis;

        if ("premium".equalsIgnoreCase(userTier)) {
            requestLimit = rateLimitProperties.getPremium().getLimit();
            windowSizeMillis = rateLimitProperties.getPremium().getWindow();
        } else {
            requestLimit = rateLimitProperties.getFree().getLimit();
            windowSizeMillis = rateLimitProperties.getFree().getWindow();
        }
        String currentCount = redisTemplate.opsForValue().get(redisKey);

        status.put("requestLimit", requestLimit);
        status.put("windowSizeMillis", windowSizeMillis);
        status.put("currentRequestCount", currentCount);
        status.put("isBanned", isUserBanned(clientId));

        return status;
    }
}
