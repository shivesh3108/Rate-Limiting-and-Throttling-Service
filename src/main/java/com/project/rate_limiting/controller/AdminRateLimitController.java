package com.project.rate_limiting.controller;

import com.project.rate_limiting.limiter.FixedWindowRateLimiter;
import com.project.rate_limiting.limiter.SlidingWindowRateLimiter;
import com.project.rate_limiting.limiter.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/rate-limit")
public class AdminRateLimitController {

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;


    @PostMapping("/reset")
    public String resetUserLimit(@RequestParam String clientId, @RequestParam String limiterType) {
        switch (limiterType) {
            case "fixed" -> fixedWindowRateLimiter.resetUserLimit(clientId);
            case "sliding" -> slidingWindowRateLimiter.resetUserLimit(clientId);
            case "token" -> tokenBucketRateLimiter.resetUserLimit(clientId);
            default -> throw new IllegalArgumentException("Invalid rate limiter type");
        };
        return "Rate limit for user " + clientId + " has been reset.";
    }


    @PostMapping("/ban")
    public String banUser(@RequestParam String clientId, @RequestParam String limiterType) {
        switch (limiterType) {
            case "fixed" -> fixedWindowRateLimiter.banUser(clientId);
            case "sliding" -> slidingWindowRateLimiter.banUser(clientId);
            case "token" -> tokenBucketRateLimiter.banUser(clientId);
            default -> throw new IllegalArgumentException("Invalid rate limiter type");
        };
        return "User " + clientId + " has been banned.";
    }


    @PostMapping("/unban")
    public String unbanUser(@RequestParam String clientId, @RequestParam String limiterType) {
        switch (limiterType) {
            case "fixed" -> fixedWindowRateLimiter.unbanUser(clientId);
            case "sliding" -> slidingWindowRateLimiter.unbanUser(clientId);
            case "token" -> tokenBucketRateLimiter.unbanUser(clientId);
            default -> throw new IllegalArgumentException("Invalid rate limiter type");
        };
        return "User " + clientId + " has been unbanned.";
    }


    @GetMapping("/status")
    public Map<String, Object> getUserRateLimitStatus(@RequestParam String clientId, @RequestParam String userTier, @RequestParam String limiterType) {
        return switch (limiterType) {
            case "fixed" -> fixedWindowRateLimiter.getUserRateLimitStatus(clientId, userTier);
            case "sliding" -> slidingWindowRateLimiter.getUserRateLimitStatus(clientId, userTier);
            case "token" -> tokenBucketRateLimiter.getUserRateLimitStatus(clientId, userTier);
            default -> throw new IllegalArgumentException("Invalid rate limiter type");
        };
    }
}
