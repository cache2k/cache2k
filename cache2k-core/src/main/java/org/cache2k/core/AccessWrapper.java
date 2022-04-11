package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * @author Jens Wilke
 */
public class AccessWrapper<V> extends ValueWrapper<V> {

  private static final AtomicIntegerFieldUpdater<AccessWrapper> UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(AccessWrapper.class, "countDown");
  private volatile int countDown;
  private final V value;
  private final int initialCount;
  private final Entry<?, V> entry;

  public static <V> Object of(Entry<?, V> e, V value, int accessCount) {
    return new AccessWrapper<V>(e, value, accessCount + 1);
  }

  public static boolean shouldRefresh(Object valueOrWrapper) {
    if (valueOrWrapper instanceof AccessWrapper) {
      return ((AccessWrapper<?>) valueOrWrapper).countDown <= 1;
    }
    return true;
  }

  public static boolean wasAccessed(Object valueOrWrapper) {
    if (valueOrWrapper instanceof AccessWrapper) {
      AccessWrapper<?> wrapper = (AccessWrapper<?>) valueOrWrapper;
      return wrapper.countDown != wrapper.initialCount;
    }
    return true;
  }

  private AccessWrapper(Entry<?, V> entry, V value, int accessCount) {
    this.value = value;
    this.entry = entry;
    this.countDown = accessCount;
    this.initialCount = accessCount;
  }

  @Override
  public V getValue() {
    Object v = getValueOrException();
    if (v instanceof ExceptionWrapper) {
      ((ExceptionWrapper<?, ?>) v).getValue();
    }
    return (V) v;
  }

  /**
   * Count down and reset wrapper when zero is reached.
   */
  public V getValueOrException() {
    int countResult = UPDATER.decrementAndGet(this);
    if (countResult <= 0) {
      synchronized (entry) {
        if (entry.getValueOrWrapper() == this) {
          entry.setValueOrWrapper(value);
        }
      }
    }
    return value;
  }

  public V getValueNoTouch() {
    return value;
  }

}
