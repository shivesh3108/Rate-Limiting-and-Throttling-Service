package com.project.rate_limiting.limiter;

import com.project.rate_limiting.config.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBucketRateLimiter{

    @Autowired
    private RateLimitProperties rateLimitProperties;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    int maxTokens;
    long refillIntervalMillis;
    private static final String BAN_KEY_PREFIX = "rate-limit:ban:";

    public boolean allowRequest(String clientId, String userTier) {
        if(isUserBanned(clientId)){
            return false;
        }

        if ("premium".equalsIgnoreCase(userTier)) {
            maxTokens = rateLimitProperties.getPremium().getLimit();
            refillIntervalMillis = rateLimitProperties.getPremium().getWindow();
        } else {
            maxTokens = rateLimitProperties.getFree().getLimit();
            refillIntervalMillis = rateLimitProperties.getFree().getWindow();
        }
        String redisKeyTokens = "token-bucket:tokens:" + clientId;
        String redisKeyStartTime = "token-bucket:start-time:" + clientId;

        long currentTime = System.currentTimeMillis();

        String startTimeStr = redisTemplate.opsForValue().get(redisKeyStartTime);
        String tokenCountStr = redisTemplate.opsForValue().get(redisKeyTokens);

        long startTime = startTimeStr == null ? currentTime : Long.parseLong(startTimeStr);
        int currentTokens = tokenCountStr == null ? maxTokens : Integer.parseInt(tokenCountStr);

        redisTemplate.opsForValue().set(redisKeyTokens, String.valueOf(currentTokens));
        redisTemplate.opsForValue().set(redisKeyStartTime, String.valueOf(startTime));


        if (currentTime - startTime >= refillIntervalMillis) {
            long refillToken = 2;
            currentTokens = Math.min(maxTokens, Integer.parseInt(tokenCountStr + refillToken));
            startTime = currentTime;
            redisTemplate.opsForValue().set(redisKeyTokens, String.valueOf(currentTokens));
            redisTemplate.opsForValue().set(redisKeyStartTime, String.valueOf(startTime));
        }

        if (currentTokens > 0) {
            long res = redisTemplate.opsForValue().decrement(redisKeyTokens);
            System.out.println(res);
            return true;
        }
        return false;
    }

    public void resetUserLimit(String clientId) {
        String redisKeyTokens = "token-bucket:tokens:" + clientId;
        String redisKeyLastRefill = "token-bucket:start-time:" + clientId;
        redisTemplate.delete(redisKeyTokens);
        redisTemplate.delete(redisKeyLastRefill);
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
        String redisKeyTokens = "token-bucket:tokens:" + clientId;

        String tokenCountStr = redisTemplate.opsForValue().get(redisKeyTokens);
        int currentTokens = tokenCountStr == null ? maxTokens : Integer.parseInt(tokenCountStr);

        status.put("requestLimit", requestLimit);
        status.put("windowSizeMillis", windowSizeMillis);
        status.put("currentBucketCount", currentTokens);
        status.put("isBanned", isUserBanned(clientId));

        return status;
    }
}
