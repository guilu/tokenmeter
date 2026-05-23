package dev.diegobarrioh.tokenmeter.application.analyzer;

import org.slf4j.MDC;

/**
 * try-with-resources helper that scopes an MDC key/value to a block. Restores the previous value on
 * close (or removes the key if it was not previously set).
 */
public final class MdcScope implements AutoCloseable {

  private final String key;
  private final String previousValue;

  private MdcScope(String key, String previousValue) {
    this.key = key;
    this.previousValue = previousValue;
  }

  public static MdcScope of(String key, String value) {
    String previous = MDC.get(key);
    if (value == null) {
      MDC.remove(key);
    } else {
      MDC.put(key, value);
    }
    return new MdcScope(key, previous);
  }

  @Override
  public void close() {
    if (previousValue == null) {
      MDC.remove(key);
    } else {
      MDC.put(key, previousValue);
    }
  }
}
