package dev.diegobarrioh.tokenmeter.domain.tokenizer;

/**
 * Describes how accurately the token count for a given model was computed.
 *
 * <ul>
 *   <li>{@link #EXACT_LOCAL} — counted locally with the model's official tokenizer (e.g. jtokkit
 *       for OpenAI models). The most accurate value available.
 *   <li>{@link #LOCAL_ESTIMATED} — counted locally via an approximate tokenizer (e.g. HuggingFace
 *       Fast Tokenizer). Slightly less accurate than the model's own tokenizer. Reserved for Phase
 *       2.
 *   <li>{@link #HEURISTIC} — derived by scaling an o200k_base reference count by a constant factor.
 *       A floor estimate; no official local tokenizer is available for this model yet.
 * </ul>
 */
public enum TokenizationPrecision {
  EXACT_LOCAL,
  LOCAL_ESTIMATED,
  HEURISTIC
}
