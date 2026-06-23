package dev.diegobarrioh.tokenmeter.infrastructure.tokenizer;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link TokenizerProfileProperties} so the binding is active when the Spring context
 * starts. {@link TokenizerProfileLoader} depends on it at construction time.
 */
@Configuration
@EnableConfigurationProperties(TokenizerProfileProperties.class)
public class TokenizerConfig {}
