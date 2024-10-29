package com.project.rate_limiting.controller;

import com.project.rate_limiting.exception.TooManyRequestsException;
import com.project.rate_limiting.limiter.FixedWindowRateLimiter;
import com.project.rate_limiting.limiter.SlidingWindowRateLimiter;
import com.project.rate_limiting.limiter.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class RateLimitController {

    @Autowired
    private FixedWindowRateLimiter fixedWindowRateLimiter;

    @Autowired
    private SlidingWindowRateLimiter slidingWindowRateLimiter;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;


    @GetMapping("/protected-resource")
    public ResponseEntity<String> getProtectedResource(@RequestParam String clientId, @RequestParam String userTier, @RequestParam String limiterType) {
        boolean allowed = switch (limiterType) {
            case "fixed" -> fixedWindowRateLimiter.allowRequest(clientId, userTier);
            case "sliding" -> slidingWindowRateLimiter.allowRequest(clientId, userTier);
            case "token" -> tokenBucketRateLimiter.allowRequest(clientId, userTier);
            default -> throw new IllegalArgumentException("Invalid rate limiter type");
        };

        if (!allowed) {
            throw new TooManyRequestsException("Rate limit exceeded. Please try again later.");
        }
        return ResponseEntity.ok("Access granted to protected resource.");
    }
}
