package com.assistent.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import(SecurityConfig.class)
@EnableConfigurationProperties(SecurityProperties.class)
public class WebMvcTestConfig {
}
