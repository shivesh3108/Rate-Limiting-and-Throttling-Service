package com.project.rate_limiting;

import com.project.rate_limiting.config.RateLimitProperties;
import com.project.rate_limiting.limiter.TokenBucketRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class TokenBucketRateLimiterTest {

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RateLimitProperties.UserTier userTier;

    @InjectMocks
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(rateLimitProperties.getFree()).thenReturn(userTier);
        when(rateLimitProperties.getPremium()).thenReturn(userTier);
    }

    @Test
    void testAllowRequestWithinLimit() {
        String clientId = "user1";
        String redisKeyTokens = "token-bucket:tokens:" + clientId;
        String redisKeyStartTime = "token-bucket:start-time:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(valueOperations.get(redisKeyStartTime)).thenReturn(String.valueOf(System.currentTimeMillis()));
        when(valueOperations.get(redisKeyTokens)).thenReturn("5");

        assertTrue(rateLimiter.allowRequest(clientId, "premium"));
        verify(valueOperations).decrement(redisKeyTokens);
    }

    @Test
    void testRejectRequestAfterLimit() {
        String clientId = "user1";
        String redisKeyTokens = "token-bucket:tokens:" + clientId;
        String redisKeyStartTime = "token-bucket:start-time:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(valueOperations.get(redisKeyStartTime)).thenReturn(String.valueOf(System.currentTimeMillis()));
        when(valueOperations.get(redisKeyTokens)).thenReturn("0");

        assertFalse(rateLimiter.allowRequest(clientId, "premium"));
    }

    @Test
    void testResetUserLimit() {
        String clientId = "user1";
        String redisKeyTokens = "token-bucket:tokens:" + clientId;
        String redisKeyStartTime = "token-bucket:start-time:" + clientId;

        rateLimiter.resetUserLimit(clientId);

        verify(redisTemplate).delete(redisKeyTokens);
        verify(redisTemplate).delete(redisKeyStartTime);
    }

    @Test
    void testBanUser() {
        String clientId = "user1";
        String banKey = "rate-limit:ban:" + clientId;

        rateLimiter.banUser(clientId);
        verify(redisTemplate).opsForValue().set(banKey, "true");
    }

    @Test
    void testUnbanUser() {
        String clientId = "user1";
        String banKey = "rate-limit:ban:" + clientId;

        rateLimiter.unbanUser(clientId);
        verify(redisTemplate).delete(banKey);
    }

    @Test
    void testIsUserBanned() {
        String clientId = "user1";
        String banKey = "rate-limit:ban:" + clientId;

        when(redisTemplate.opsForValue().get(banKey)).thenReturn("true");
        assertTrue(rateLimiter.isUserBanned(clientId));
        when(redisTemplate.opsForValue().get(banKey)).thenReturn(null);
        assertFalse(rateLimiter.isUserBanned(clientId));
    }

    @Test
    void testGetUserRateLimitStatus() {
        String clientId = "user1";
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(valueOperations.get("token-bucket:tokens:" + clientId)).thenReturn("3");

        Map<String, Object> status = rateLimiter.getUserRateLimitStatus(clientId, "premium");

        assertEquals(5, status.get("requestLimit"));
        assertEquals(60000L, status.get("windowSizeMillis"));
        assertEquals(3, status.get("currentBucketCount"));
        assertFalse((Boolean) status.get("isBanned"));
    }
}