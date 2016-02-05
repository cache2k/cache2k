package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.impl.InternalCache;
import org.cache2k.impl.InternalCacheInfo;

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

  InternalCacheInfo info;
  List<Counter> counters = new ArrayList<Counter>();
  boolean disable = false;

  public Statistics() {
  }

  public Statistics(final boolean _disable) {
    disable = _disable;
  }

  public final Counter readCount = new Counter("read") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getReadUsageCnt();
    }
  };

  public final Counter putCount = new Counter("put") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getPutCnt();
    }
  };

  public final Counter missCount = new Counter("miss") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getMissCnt();
    }
  };

  public final Counter removeCount = new Counter("remove") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getRemovedCnt();
    }
  };

  public final Counter loadCount = new Counter("load") {
    @Override
    protected long getCounterValue(final InternalCacheInfo inf) {
      return inf.getFetchCnt();
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

  public abstract class Counter {

    long baseValue;
    String name;

    public Counter(final String _name) {
      name = _name;
      counters.add(this);
    }

    protected abstract long getCounterValue(InternalCacheInfo inf);

    public Statistics reset() {
      baseValue += get();
      return Statistics.this;
    }

    /**
     * The counter value, actually the delta value since the last reset.
     */
    public long get() {
      return getCounterValue(info) - baseValue;
    }

    /**
     * Asserts on an expected counter value and resets it. By resetting
     * this operation can be followed by {@link #expectAllZero()} to check
     * that all other counters are 0.
     */
    public Statistics expect(long v) {
      if (!disable) {
        assertEquals(name + " counter", v, get());
      }
      reset();
      return Statistics.this;
    }

  }

}
