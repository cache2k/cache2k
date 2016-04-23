package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.integration.ExceptionPropagator;

/**
 * We use instances of the exception wrapper for the value field in the entry.
 * This way we can store exceptions without needing additional memory, if no exceptions
 * happen.
 *
 * @author Jens Wilke; created: 2013-07-12
 */
public class ExceptionWrapper<K> implements ExceptionPropagator.CachedExceptionInformation<K> {

  Throwable exception;
  long loadTime;
  long until;
  K key;

  public ExceptionWrapper(Throwable ex) {
    exception = ex;
  }

  public ExceptionWrapper(final K _key, final Throwable _exception, final long _loadTime) {
    exception = _exception;
    key = _key;
    loadTime = _loadTime;
  }

  @Override
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

  public String toString() {
    return "ExceptionWrapper{" + exception.toString() + "}";
  }

}
