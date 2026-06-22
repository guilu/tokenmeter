package dev.diegobarrioh.tokenmeter.application.tokenizer;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.ModelTokenizationProfile;
import dev.diegobarrioh.tokenmeter.domain.tokenizer.TokenCounterStrategy;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * {@link TokenCounter} implementation backed by jtokkit.
 *
 * <p>Supports all OpenAI BPE encodings: {@code O200K_BASE}, {@code CL100K_BASE}, {@code P50K_BASE}
 * and {@code R50K_BASE}. {@link Encoding} instances are resolved lazily per encoding name and
 * cached in a {@link ConcurrentHashMap} so that the underlying jtokkit registry is only consulted
 * once per distinct encoding across the lifetime of the component.
 */
@Component
public class JtokkitTokenCounter implements TokenCounter {

  private final EncodingRegistry registry;
  private final ConcurrentHashMap<String, Encoding> cache;

  public JtokkitTokenCounter() {
    this(Encodings.newDefaultEncodingRegistry());
  }

  JtokkitTokenCounter(EncodingRegistry registry) {
    this.registry = registry;
    this.cache = new ConcurrentHashMap<>();
  }

  @Override
  public boolean supports(ModelTokenizationProfile profile) {
    return profile.strategy() == TokenCounterStrategy.JTOKKIT;
  }

  @Override
  public long count(String text, ModelTokenizationProfile profile) {
    if (text == null || text.isEmpty()) {
      return 0L;
    }
    Encoding encoding = resolveEncoding(profile.encoding());
    return encoding.countTokensOrdinary(text);
  }

  private Encoding resolveEncoding(String encodingName) {
    return cache.computeIfAbsent(
        encodingName.toUpperCase(Locale.ROOT),
        key -> {
          EncodingType type = EncodingType.valueOf(key);
          return registry.getEncoding(type);
        });
  }
}
