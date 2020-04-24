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

/**
 * We use instances of the exception wrapper for the value field in the entry.
 * This way we can store exceptions without needing additional memory, if no exceptions
 * happen.
 *
 * @author Jens Wilke; created: 2013-07-12
 */
public class ExceptionWrapper<K> implements ExceptionInformation {

  private Throwable exception;
  private long loadTime;
  private long until;
  private int count;
  private long since;
  private K key;

  /**
   * Constructs a wrapper with time set to 0. Used for testing purposes.
   */
  public ExceptionWrapper(Throwable ex) {
    this(0, ex);
  }

  public ExceptionWrapper(long now, Throwable ex) {
    loadTime = now;
    exception = ex;
  }

  /**
   * Take over exception information from the entry, which either has
   * no exception, an existing cached exception or a suppressed exception.
   */
  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final Entry e) {
    ExceptionInformation _recentExceptionInfo;
    Object _oldValue = e.getValueOrException();
    if (_oldValue instanceof ExceptionWrapper) {
      _recentExceptionInfo = (ExceptionInformation) _oldValue;
    } else {
      _recentExceptionInfo = e.getSuppressedLoadExceptionInformation();
    }
    init(_key, _exception, _loadTime, _recentExceptionInfo);
  }

  public ExceptionWrapper(final K _key, final Throwable _exception,
                          final long _loadTime, final ExceptionInformation w) {
    init(_key, _exception, _loadTime, w);
  }

  private void init(final K _key, final Throwable _exception,
                    final long _loadTime, final ExceptionInformation w) {
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

  public String toString() {
    return "ExceptionWrapper{" + exception.toString() + "}";
  }

}
