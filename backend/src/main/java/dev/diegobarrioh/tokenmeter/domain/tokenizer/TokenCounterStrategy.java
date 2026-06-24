package dev.diegobarrioh.tokenmeter.domain.tokenizer;

/**
 * Identifies the counter strategy that implements a {@link ModelTokenizationProfile}.
 *
 * <ul>
 *   <li>{@link #JTOKKIT} — uses the jtokkit library; supports all OpenAI BPE encodings.
 *   <li>{@link #HEURISTIC} — scales an o200k_base reference count by a provider-specific factor.
 *   <li>{@link #HF_LOCAL} — uses a vendored HuggingFace Fast Tokenizer ({@code tokenizer.json})
 *       loaded from the classpath via the DJL HuggingFace Tokenizers library. Provides exact token
 *       counts for models such as DeepSeek V3 and Qwen2.x whose native vocabularies differ from
 *       OpenAI BPE encodings.
 * </ul>
 */
public enum TokenCounterStrategy {
  JTOKKIT,
  HEURISTIC,
  HF_LOCAL
}
