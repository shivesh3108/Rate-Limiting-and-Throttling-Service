package com.project.rate_limiting.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "ratelimit")
public class RateLimitProperties {

    private UserTier free;
    private UserTier premium;

    @Setter
    @Getter
    public static class UserTier {
        private int limit;
        private long window;
    }
}
