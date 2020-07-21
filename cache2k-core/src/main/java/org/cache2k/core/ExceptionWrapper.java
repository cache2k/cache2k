package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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
public class ExceptionWrapper<K> implements ExceptionInformation, CacheEntry<K, Void> {

  final private Throwable exception;
  final private long loadTime;
  final private int count;
  final private long since;
  final private K key;
  final private ExceptionPropagator<K> propagator;
  final private long until;

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

  public ExceptionWrapper(final K key, long now, Throwable ex, ExceptionPropagator<K> p) {
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
  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final Entry e,
                          ExceptionPropagator<K> p) {
    this(_key, _exception, _loadTime,
      (e.getValueOrException() instanceof ExceptionWrapper) ?
        (ExceptionInformation) e.getValueOrException() :
        e.getSuppressedLoadExceptionInformation(),
      p);
  }

  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final ExceptionInformation w,
                          ExceptionPropagator<K> p) {
    propagator = p;
    exception = _exception;
    key = _key;
    loadTime = _loadTime;
    if (w != null) {
      since = w.getSinceTime();
      count = w.getRetryCount() + 1;
    } else {
      since = loadTime;
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
  public Object propagateException() {
    if (1 == 1) {
      throw getExceptionPropagator().propagateException(key, this);
    }
    return null;
  }

  public String toString() {
    return "ExceptionWrapper{" + exception.toString() + "}";
  }

}
