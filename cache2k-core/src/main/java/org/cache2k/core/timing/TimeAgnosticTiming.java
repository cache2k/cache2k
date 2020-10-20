package org.cache2k.core.timing;

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

import org.cache2k.core.Entry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.io.LoadExceptionInfo;

/**
 * Base class for all timing handlers that actually need not to know the current time.
 */
public abstract class TimeAgnosticTiming<K, V> extends Timing<K, V> {

  /** Used as default */
  public static final Timing ETERNAL = new EternalTiming();
  public static final Timing ETERNAL_IMMEDIATE = new EternalImmediate();
  public static final Timing IMMEDIATE = new Immediate();

  static class EternalImmediate<K, V> extends TimeAgnosticTiming<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return 0;
    }

  }

  static class Immediate<K, V> extends TimeAgnosticTiming<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return 0;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return 0;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return 0;
    }
  }

  static class EternalTiming<K, V> extends TimeAgnosticTiming<K, V> {

    @Override
    public long calculateNextRefreshTime(Entry<K, V> e, V v, long loadTime) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long cacheExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return ExpiryPolicy.ETERNAL;
    }

    @Override
    public long suppressExceptionUntil(Entry<K, V> e, LoadExceptionInfo inf) {
      return ExpiryPolicy.ETERNAL;
    }

  }
}
