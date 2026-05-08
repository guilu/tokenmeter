package dev.diegobarrioh.tokenmeter.application.tokenizer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenAiTokenCounterTest {
  private final OpenAiTokenCounter tokenCounter = new OpenAiTokenCounter();

  @Test
  void usesOpenAiCompatibleO200kBaseEncoding() {
    assertThat(tokenCounter.encodingName()).isEqualTo("o200k_base");
  }

  @Test
  void tokenizesRepresentativeSourceAndMarkupContent() {
    assertThat(tokenCounter.count("public class App {}\n")).isPositive();
    assertThat(tokenCounter.count("export const value: string = 'hello';\n")).isPositive();
    assertThat(tokenCounter.count("# Heading\n\nMarkdown body\n")).isPositive();
    assertThat(tokenCounter.count("{\"enabled\":true}\n")).isPositive();
    assertThat(tokenCounter.count("app:\n  enabled: true\n")).isPositive();
  }

  @Test
  void emptyContentHasNoTokens() {
    assertThat(tokenCounter.count("")).isZero();
  }
}
