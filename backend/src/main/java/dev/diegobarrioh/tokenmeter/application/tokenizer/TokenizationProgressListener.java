package dev.diegobarrioh.tokenmeter.application.tokenizer;

/**
 * Callback invoked by {@link RepositoryTokenizationService} after each file is tokenized. Allows
 * callers to report granular progress without coupling the tokenizer to any infrastructure or
 * emitter type.
 *
 * <p>Implementations must be non-null. A no-op implementation can be supplied as {@code (p, t, tok)
 * -> {}}.
 */
@FunctionalInterface
public interface TokenizationProgressListener {

  /**
   * Called once per file, immediately after that file's tokens have been counted and added to the
   * running total.
   *
   * @param filesProcessed cumulative count of files processed so far (1-based, never 0)
   * @param totalFiles total number of files to be tokenized in this run
   * @param tokensCountedSoFar running sum of tokens counted across all processed files
   */
  void onProgress(long filesProcessed, long totalFiles, long tokensCountedSoFar);
}
