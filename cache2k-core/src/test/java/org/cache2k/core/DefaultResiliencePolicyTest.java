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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class DefaultResiliencePolicyTest {

  /** Provide unique standard cache per method */
  @Rule public IntCacheRule target = new IntCacheRule();

  DefaultResiliencePolicy extractDefaultPolicy() {
    TimingHandler h = extractHandler();
    if (!(h instanceof TimingHandler.Static)) {
      fail(TimingHandler.Static.class + " expected");
    }
    ResiliencePolicy p = ((TimingHandler.Static) h).resiliencePolicy;
    if (!(p instanceof DefaultResiliencePolicy)) {
      fail(DefaultResiliencePolicy.class + " expected");
    }
    return (DefaultResiliencePolicy) p;
  }

  TimingHandler extractHandler() {
    return target.getCache().requestInterface(HeapCache.class).timing;
  }

  /**
   * Values do not expire, exceptions are not suppressed and an immediately
   * retry is done.
   */
  @Test
  public void eternal() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    assertTrue(extractHandler() instanceof TimingHandler.EternalImmediate);
  }

  /**
   * Values do not expire. If a loader exception happens the exception
   * is propagated and the first retry is done after approximately (plus randomization)
   * 3 seconds. After second and the following exceptions the retry interval will
   * be increased after a maximum of 30 seconds is reached.
   *
   * <p>For a cached value, a load can be triggered by {@code reload()}. If an
   * exception happens in this case it is suppressed for 30 seconds. A first retry
   * is done after 3 seconds.
   */
  @Test
  public void eternal_duration30s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      .resilienceDuration(30, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), p.getRetryInterval());
  }

  @Test
  public void eternal_duration30s_retry10s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      .resilienceDuration(30, TimeUnit.SECONDS)
      .retryInterval(10, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), p.getRetryInterval());
  }

  /**
   * No suppression, because eternal. The only way that a reload can be triggered
   * is with a reload operation. In this case we do not want suppression, unless
   * specified explicitly.
   */
  @Test
  public void eternal_retry10s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      .retryInterval(10, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(0, p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(10), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), p.getRetryInterval());
  }

  /**
   * This is values=eternal, exceptions=immediate.
   */
  @Test
  public void eternal_retry0s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      .retryInterval(0, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    assertTrue(extractHandler() instanceof TimingHandler.EternalImmediate);
  }

  @Test
  public void expiry0() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(0, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    assertTrue(extractHandler() instanceof TimingHandler.Immediate);
  }

  @Test
  public void expiry0_retry20s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(0, TimeUnit.MINUTES)
      .retryInterval(20, TimeUnit.SECONDS)
      .suppressExceptions(true) /* has no effect! */
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals("no exception suppression", 0, p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(20), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(20), p.getRetryInterval());
  }

  @Test
  public void expiry0_retry20s_resilience20m() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(0, TimeUnit.MINUTES)
      .retryInterval(20, TimeUnit.SECONDS)
      .resilienceDuration(5, TimeUnit.MINUTES)
      .suppressExceptions(true) /* has no effect! */
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.MINUTES.toMillis(5), p.getResilienceDuration());
    assertEquals(TimeUnit.MINUTES.toMillis(5), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(20), p.getRetryInterval());
  }

  /**
   * Values expire after 10 minutes. Exceptions are suppressed for 10 minutes
   * as well, if possible. A retry attempt is made until 1 minute. If the cache
   * continuously receives exceptions for a key, the retry intervals are
   * exponentially increased up to 10 minutes.
   */
  @Test
  public void expiry10m() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(10, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.MINUTES.toMillis(10), p.getResilienceDuration());
    assertEquals(TimeUnit.MINUTES.toMillis(10), p.getMaxRetryInterval());
    assertEquals(TimeUnit.MINUTES.toMillis(1), p.getRetryInterval());
  }

  @Test
  public void expiry10m_duration30s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .resilienceDuration(30, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), p.getRetryInterval());
  }

  @Test
  public void expiry10m_retry10s() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .retryInterval(10, TimeUnit.SECONDS)
      /* ... set loader ... */
      .build();
    target.setCache(c);
    DefaultResiliencePolicy p = extractDefaultPolicy();
    assertEquals(TimeUnit.MINUTES.toMillis(10), p.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(10), p.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), p.getRetryInterval());
  }

  @Test(expected = IllegalArgumentException.class)
  public void noSuppress_duration10m() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() {}
      .eternal(true)
      .resilienceDuration(10, TimeUnit.MINUTES)
      .suppressExceptions(false)
      /* ... set loader ... */
      .build();
  }

}
