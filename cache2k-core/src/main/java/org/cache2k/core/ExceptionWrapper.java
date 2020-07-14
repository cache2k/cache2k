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

 import org.cache2k.integration.ExceptionInformation;
 import org.cache2k.integration.ExceptionPropagator;

/**
 * We use instances of the exception wrapper for the value field in the entry.
 * This way we can store exceptions without needing additional memory, if no exceptions
 * happen.
 *
 * @author Jens Wilke
 */
public class ExceptionWrapper<K> implements ExceptionInformation {

  private Throwable exception;
  private long loadTime;
  private long until;
  private int count;
  private long since;
  private K key;
  private ExceptionPropagator<K> propagator;

  public ExceptionWrapper(long now, Throwable ex, ExceptionPropagator<K> p) {
    loadTime = now;
    exception = ex;
    propagator = p;
  }

  /**
   * Take over exception information from the entry, which either has
   * no exception, an existing cached exception or a suppressed exception.
   */
  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final Entry e,
                          ExceptionPropagator<K> p) {
    ExceptionInformation _recentExceptionInfo;
    Object _oldValue = e.getValueOrException();
    if (_oldValue instanceof ExceptionWrapper) {
      _recentExceptionInfo = (ExceptionInformation) _oldValue;
    } else {
      _recentExceptionInfo = e.getSuppressedLoadExceptionInformation();
    }
    init(_key, _exception, _loadTime, _recentExceptionInfo, p);
  }

  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final ExceptionInformation w,
                          ExceptionPropagator<K> p) {
    init(_key, _exception, _loadTime, w, p);
  }

  private void init(final K _key, final Throwable _exception,
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
    }
  }

  public void setUntil(final long _until) {
    until = _until;
  }

  public K getKey() {
    return key;
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
