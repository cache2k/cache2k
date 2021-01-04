package org.cache2k.test.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Abstract how to access counter values from the cache and a fluent API
 * to do assertions on the expected values for crispy tests.
 *
 * <p>The basic idea is not to do assertions on the absolute counter values
 * but on the delta values. The exposed interface for the tests is the
 * ability to retrieve the counter value, assert on a value and to reset
 * one or all values.
 *
 * @author Jens Wilke
 */
public class Statistics {

  private InternalCacheInfo info;
  private List<Counter> counters = new ArrayList<Counter>();
  private boolean disable = false;

  public Statistics() { }

  public Statistics(final boolean _disable) {
    disable = _disable;
  }

  public final Counter getCount = new Counter("get") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getGetCount();
    }
  };

  public final Counter putCount = new Counter("put") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getPutCount();
    }
  };

  public final Counter missCount = new Counter("miss") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getMissCount();
    }
  };

  public final Counter removeCount = new Counter("remove") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getRemoveCount();
    }
  };

  public final Counter loadCount = new Counter("load") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getLoadCount();
    }
  };

  public final Counter expiredCount = new Counter("expired") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getExpiredCount();
    }
  };

  /** Reset all counters */
  public void reset() {
    for (Counter c : counters) {
      c.reset();
    }
  }

  /** Get the latest counter values from the cache. */
  public Statistics sample(Cache<?,?> c) {
    info = c.requestInterface(InternalCache.class).getLatestInfo();
    return this;
  }

  /** Checks that all counter deltas are zero */
  public void expectAllZero() {
    for (Counter c : counters) {
      c.expect(0);
    }

  }

  public void dump() {
    System.err.println(info);
  }

  public abstract class Counter {

    long baseValue;
    String name;

    public Counter(final String _name) {
      name = _name;
      counters.add(this);
    }

    protected abstract long getCounterValue(InternalCacheInfo inf);

    public final Statistics reset() {
      baseValue += get();
      return Statistics.this;
    }

    /**
     * The counter value, actually the delta value since the last reset.
     */
    public final long get() {
      return getCounterValue(info) - baseValue;
    }

    /**
     * Asserts on an expected counter value and resets it. By resetting
     * this operation can be followed by {@link #expectAllZero()} to check
     * that all other counters are 0.
     */
    public final Statistics expect(long v) {
      if (!disable) {
        assertEquals(name + " counter", v, get());
      }
      reset();
      return Statistics.this;
    }

  }

}
