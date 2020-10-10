package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

 import org.cache2k.CacheEntry;
 import org.cache2k.integration.ExceptionInformation;
 import org.cache2k.integration.ExceptionPropagator;

/**
 * The exception wrapper is used in the value field of the entry, in case of an exception.
 * This way we can store exceptions without needing additional memory, if no exceptions
 * happen.
 *
 * <p>The wrapper is immutable and implements cache entry, this way it can be used
 * returned cache entry directly.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class ExceptionWrapper<K> implements ExceptionInformation, CacheEntry<K, Void> {

  private final Throwable exception;
  private final long loadTime;
  private final int count;
  private final long since;
  private final K key;
  private final ExceptionPropagator<K> propagator;
  private final long until;

  /**
   * Copy constructor to set until.
   */
  public ExceptionWrapper(ExceptionWrapper<K> w, long until) {
    this.exception = w.exception;
    this.loadTime = w.loadTime;
    this.count = w.count;
    this.since = w.since;
    this.key = w.key;
    this.propagator = w.propagator;
    this.until = until;
  }

  public ExceptionWrapper(K key, long now, Throwable ex, ExceptionPropagator<K> p) {
    this.key = key;
    loadTime = since = now;
    exception = ex;
    propagator = p;
    until = 0;
    count = 0;
  }

  /**
   * Take over exception information from the entry, which either has
   * no exception, an existing cached exception or a suppressed exception.
   */
  public ExceptionWrapper(K key, Throwable exception,
                          long loadTime, Entry e,
                          ExceptionPropagator<K> p) {
    this(key, exception, loadTime,
      (e.getValueOrException() instanceof ExceptionWrapper) ?
        (ExceptionInformation) e.getValueOrException() :
        e.getSuppressedLoadExceptionInformation(),
      p);
  }

  public ExceptionWrapper(K key, Throwable exception,
                          long loadTime, ExceptionInformation w,
                          ExceptionPropagator<K> p) {
    propagator = p;
    this.exception = exception;
    this.key = key;
    this.loadTime = loadTime;
    if (w != null) {
      since = w.getSinceTime();
      count = w.getRetryCount() + 1;
    } else {
      since = this.loadTime;
      count = 0;
    }
    until = 0;
  }

  public K getKey() {
    return key;
  }

  @Override
  public Void getValue() {
    propagateException();
    return null;
  }

  @SuppressWarnings("deprecation")
  @Override
  public long getLastModification() {
    throw new UnsupportedOperationException();
  }

  @Override
  public ExceptionPropagator getExceptionPropagator() { return propagator; }

  @Override
  public Throwable getException() {
    return exception;
  }

  @Override
  public long getUntil() {
    return until;
  }

  @Override
  public long getLoadTime() {
    return loadTime;
  }

  @Override
  public int getRetryCount() {
    return count;
  }

  @Override
  public long getSinceTime() {
    return since;
  }

  /**
   * Propagate the exception based by the information here. Used when it is tried to
   * access the value.
   */
  @SuppressWarnings("unchecked")
  public void propagateException() {
    throw getExceptionPropagator().propagateException(key, this);
  }

  /**
   * The exception wrapper instance is also used as {@link CacheEntry} directly and
   * returned by {@link org.cache2k.Cache#getEntry(Object)}
   */
  public String toString() {
    return "ExceptionWrapper{key=" + getKey() + ", exception=" + exception + "}";
  }

}
