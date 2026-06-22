package dev.diegobarrioh.tokenmeter.domain.tokenizer;

/**
 * Identifies the counter strategy that implements a {@link ModelTokenizationProfile}.
 *
 * <ul>
 *   <li>{@link #JTOKKIT} — uses the jtokkit library; supports all OpenAI BPE encodings.
 *   <li>{@link #HEURISTIC} — scales an o200k_base reference count by a provider-specific factor.
 * </ul>
 *
 * <p>Phase 2 will add {@code HF_LOCAL} for HuggingFace Fast Tokenizer integration.
 */
public enum TokenCounterStrategy {
  JTOKKIT,
  HEURISTIC
}
