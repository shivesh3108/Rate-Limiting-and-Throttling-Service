package com.project.rate_limiting;

import com.project.rate_limiting.config.RateLimitProperties;
import com.project.rate_limiting.limiter.SlidingWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class SlidingWindowRateLimiterTest {

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private RateLimitProperties.UserTier userTier;

    @InjectMocks
    private SlidingWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(rateLimitProperties.getFree()).thenReturn(userTier);
        when(rateLimitProperties.getPremium()).thenReturn(userTier);
    }

    @Test
    void testAllowRequestWithinLimit() {
        String clientId = "user1";
        String redisKey = "sliding-window:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(zSetOperations.zCard(redisKey)).thenReturn(0L, 1L, 2L, 3L, 4L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest(clientId, "premium"));
        }
    }

    @Test
    void testRejectRequestAfterLimit() {
        String clientId = "user1";
        String redisKey = "sliding-window:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(zSetOperations.zCard(redisKey)).thenReturn(0L, 1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            rateLimiter.allowRequest(clientId, "premium");
        }
        assertFalse(rateLimiter.allowRequest(clientId, "premium"));
    }

    @Test
    void testResetUserLimit() {
        String clientId = "user1";
        String redisKey = "sliding-window:" + clientId;

        rateLimiter.resetUserLimit(clientId);
        verify(redisTemplate).delete(redisKey);
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

        Map<String, Object> status = rateLimiter.getUserRateLimitStatus(clientId, "premium");

        assertEquals(5, status.get("requestLimit"));
        assertEquals(60000L, status.get("windowSizeMillis"));
        assertFalse((Boolean) status.get("isBanned"));
    }
}