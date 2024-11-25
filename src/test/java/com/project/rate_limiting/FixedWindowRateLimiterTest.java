package com.project.rate_limiting;

import com.project.rate_limiting.config.RateLimitProperties;
import com.project.rate_limiting.limiter.FixedWindowRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class FixedWindowRateLimiterTest {

    @Mock
    private RateLimitProperties rateLimitProperties;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RateLimitProperties.UserTier userTier;

    @InjectMocks
    private FixedWindowRateLimiter rateLimiter;

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
        String redisKey = "rate-limit:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(valueOperations.increment(redisKey)).thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest(clientId, "premium"));
        }
        verify(redisTemplate).expire(redisKey, Duration.ofMillis(60000L));
    }

    @Test
    void testRejectRequestAfterLimit() {
        String clientId = "user1";
        String redisKey = "rate-limit:" + clientId;
        when(rateLimitProperties.getPremium().getLimit()).thenReturn(5);
        when(rateLimitProperties.getPremium().getWindow()).thenReturn(60000L);
        when(valueOperations.increment(redisKey)).thenReturn(1L, 2L, 3L, 4L, 5L, 6L);

        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allowRequest(clientId, "premium"));
        }
        verify(redisTemplate).expire(redisKey, Duration.ofMillis(60000L));
        assertFalse(rateLimiter.allowRequest(clientId, "premium"));
    }

    @Test
    void testAllowRequestForFreeUserWithinLimit() {
        String clientId = "user2";
        String redisKey = "rate-limit:" + clientId;
        when(rateLimitProperties.getFree().getLimit()).thenReturn(3);
        when(rateLimitProperties.getFree().getWindow()).thenReturn(60000L);
        when(valueOperations.increment(redisKey)).thenReturn(1L, 2L, 3L);

        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimiter.allowRequest(clientId, "free"));
        }
        verify(redisTemplate).expire(redisKey, Duration.ofMillis(60000L));
    }

    @Test
    void testRejectRequestForFreeUserAfterLimit() {
        String clientId = "user2";
        String redisKey = "rate-limit:" + clientId;
        when(rateLimitProperties.getFree().getLimit()).thenReturn(3);
        when(rateLimitProperties.getFree().getWindow()).thenReturn(60000L);
        when(valueOperations.increment(redisKey)).thenReturn(1L, 2L, 3L, 4L);

        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimiter.allowRequest(clientId, "free"));
        }
        verify(redisTemplate).expire(redisKey, Duration.ofMillis(60000L));
        assertFalse(rateLimiter.allowRequest(clientId, "free"));
    }


    @Test
    void testResetUserLimit() {
        String clientId = "user1";
        String redisKey = "rate-limit:" + clientId;

        rateLimiter.resetUserLimit(clientId);
        verify(redisTemplate).delete(redisKey);
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
    void testRejectRequestForBannedUser() {
        String clientId = "user3";
        String banKey = "rate-limit:ban:" + clientId;
        when(redisTemplate.opsForValue().get(banKey)).thenReturn("true");
        assertFalse(rateLimiter.allowRequest(clientId, "premium"));
    }
}