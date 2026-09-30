package com.exam.activity.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class RedisResilienceConfig {

    @Bean
    public LettuceClientConfigurationBuilderCustomizer lettuceCustomizer() {
        return builder -> {
            SocketOptions socketOptions = SocketOptions.builder()
                    .connectTimeout(Duration.ofMillis(500))
                    .build();
            ClientOptions clientOptions = ClientOptions.builder()
                    .socketOptions(socketOptions)
                    .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(500)))
                    .autoReconnect(true)
                    .build();
            builder.clientOptions(clientOptions).commandTimeout(Duration.ofMillis(500));
        };
    }
}
