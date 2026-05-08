package dev.diegobarrioh.tokenmeter.application.tokenizer;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.stereotype.Component;

@Component
public class OpenAiTokenCounter {
  private final Encoding encoding;

  public OpenAiTokenCounter() {
    this(Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.O200K_BASE));
  }

  OpenAiTokenCounter(Encoding encoding) {
    this.encoding = encoding;
  }

  public String encodingName() {
    return encoding.getName();
  }

  public long count(String text) {
    if (text.isEmpty()) {
      return 0;
    }
    return encoding.countTokensOrdinary(text);
  }
}
