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

import org.cache2k.core.DefaultResiliencePolicy;
import org.cache2k.integration.ExceptionInformation;
import org.cache2k.integration.ExceptionPropagator;
import org.cache2k.integration.ResiliencePolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

/**
 * Unit test with out cache for default resilience policy.
 *
 * @author Jens Wilke
 * @see DefaultResiliencePolicy
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Category(FastTests.class)
public class DefaultResiliencePolicyUnitTest {

  @Test
  public void testBackoffPower() {
    assertEquals(10, 10 * Math.pow(1.5, 0), 0.1);
    assertEquals(15, 10 * Math.pow(1.5, 1), 0.1);
    assertEquals(22.5, 10 * Math.pow(1.5, 2), 0.1);
    assertEquals(33.75, 10 * Math.pow(1.5, 3), 0.1);
  }

  @Test
  public void testStandardProperties() {
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    assertEquals(0.5, p.getRandomization(), 0.1);
    assertEquals(1.5, p.getMultiplier(), 0.1);
  }

  /**
   * Suppress duration defaults to expiry if not set.
   */
  @Test
  public void testDefaultSuppressDuration() {
    DefaultResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(100000, p.getResilienceDuration());
  }

  private DefaultResiliencePolicy getDefaultResiliencePolicy10000() {
    ResiliencePolicy.Context ctx = new CtxBean(100000, -1, -1, -1);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.init(ctx);
    return p;
  }

  /**
   * Max retry interval is expiry time.
   */
  @Test
  public void testDefaultMaxRetryInterval() {
    DefaultResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(100000, p.getMaxRetryInterval());
  }

  /**
   * 10% of expireAfter or resilienceDuration write
   */
  @Test
  public void testDefaultRetryInterval() {
    DefaultResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(10000, p.getRetryInterval());
  }

  /**
   * Expiry 240s, resilience duration 30s.
   */
  @Test
  public void testWithExpiryAndResilienceDuration() {
    ResiliencePolicy.Context ctx =
      new CtxBean(240000, -1, -1, 30000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.init(ctx);
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, resilience duration 30s.
   */
  @Test
  public void testWithExpiryAndResilienceDuration10Min30Sec() {
    ResiliencePolicy.Context ctx =
      new CtxBean(10 * 60 * 1000, -1, -1, 30000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.init(ctx);
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, retry interval 10s.
   */
  @Test
  public void testWithExpiryAndRetryInterval() {
    ResiliencePolicy.Context ctx =
      new CtxBean(240000, 10000, -1, -1);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.init(ctx);
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, max retry interval 10s.
   */
  @Test
  public void testWithExpiryAndMaxRetryInterval() {
    ResiliencePolicy.Context ctx =
      new CtxBean(240000, -1, 10000, -1);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.init(ctx);
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  @Test
  public void testRandomization() {
    ResiliencePolicy.Context ctx =
      new CtxBean(10000, 100, 500, 5000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.setRandomization(0.5);
    p.init(ctx);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long min = Long.MAX_VALUE;
    long max = 0;
    long t0 = p.suppressExceptionUntil("key", b, null);
    boolean oneDifferent = false;
    for (int i = 0; i < 100; i++) {
      long t = p.suppressExceptionUntil("key", b, null);
      if (t != t0) {
        oneDifferent = true;
      }
      min = Math.min(t, min);
      max = Math.max(t, max);
    }
    assertTrue(oneDifferent);
    assertThat((max - min), greaterThanOrEqualTo(50L / 2));
    assertThat((max - min), lessThanOrEqualTo(50L));
    assertThat(min, greaterThanOrEqualTo(50L));
  }

  @Test
  public void testCustomMultiplier() {
    ResiliencePolicy.Context ctx =
      new CtxBean(10000, 100, 500, 5000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.setRandomization(0.0);
    assertEquals(0, p.getRandomization(), 0.1);
    p.setMultiplier(2);
    p.init(ctx);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long t = p.suppressExceptionUntil("key", b, null);
    assertEquals(100, t);
    b.incrementRetryCount();
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(200, t);
  }

  @Test
  public void testSuppress() {
    ResiliencePolicy.Context ctx =
      new CtxBean(10000, 100, 500, 5000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.setRandomization(0.0);
    assertEquals(1.5, p.getMultiplier(), 0.1);
    p.init(ctx);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long t = p.suppressExceptionUntil("key", b, null);
    assertEquals(100, t);
    b.incrementRetryCount();
    b.setLoadTime(107);
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(150, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(300);
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(225, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(582);
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(337, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(934);
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(500, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(1534);
    t = p.suppressExceptionUntil("key", b, null);
    assertEquals(500, t - b.getLoadTime());
  }

  @Test
  public void testCache() {
    ResiliencePolicy.Context ctx =
      new CtxBean(10000, 100, 500, 5000);
    DefaultResiliencePolicy p = new DefaultResiliencePolicy();
    p.setRandomization(0.0);
    assertEquals(1.5, p.getMultiplier(), 0.1);
    p.init(ctx);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long t = p.retryLoadAfter("key", b);
    assertEquals(100, t);
    b.incrementRetryCount();
    b.setLoadTime(107);
    t = p.retryLoadAfter("key", b);
    assertEquals(150, t - b.getLoadTime());
    b.incrementRetryCount();
    b.incrementRetryCount();
    b.incrementRetryCount();
    b.incrementRetryCount();
    b.incrementRetryCount();
    b.setLoadTime(0);
    t = p.retryLoadAfter("key", b);
    assertEquals(500, t);
  }

  static class CtxBean implements ResiliencePolicy.Context {

    long expireAfterWriteMillis;
    long resilienceDurationMillis;
    long retryIntervalMillis;
    long maxRetryIntervalMillis;

    CtxBean(long expireAfterWriteMillis,
            long retryIntervalMillis,
            long maxRetryIntervalMillis,
            long resilienceDurationMillis) {
      this.expireAfterWriteMillis = expireAfterWriteMillis;
      this.retryIntervalMillis = retryIntervalMillis;
      this.maxRetryIntervalMillis = maxRetryIntervalMillis;
      this.resilienceDurationMillis = resilienceDurationMillis;
    }

    @Override
    public long getExpireAfterWriteMillis() {
      return expireAfterWriteMillis;
    }

    @Override
    public long getResilienceDurationMillis() {
      return resilienceDurationMillis;
    }

    @Override
    public long getRetryIntervalMillis() {
      return retryIntervalMillis;
    }

    @Override
    public long getMaxRetryIntervalMillis() {
      return maxRetryIntervalMillis;
    }

  }

  static class InfoBean implements ExceptionInformation {

    int retryCount;
    Throwable exception;
    long loadTime;
    long sinceTime;
    long until;

    @Override
    public ExceptionPropagator getExceptionPropagator() { return null; }

    public void incrementRetryCount() {
      retryCount++;
    }

    @Override
    public Throwable getException() {
      return exception;
    }

    @Override
    public long getLoadTime() {
      return loadTime;
    }

    @Override
    public int getRetryCount() {
      return retryCount;
    }

    @Override
    public long getSinceTime() {
      return sinceTime;
    }

    @Override
    public long getUntil() {
      return until;
    }

    public void setException(Throwable exception) {
      this.exception = exception;
    }

    public void setLoadTime(long loadTime) {
      this.loadTime = loadTime;
    }

    public void setRetryCount(int retryCount) {
      this.retryCount = retryCount;
    }

    public void setSinceTime(long sinceTime) {
      this.sinceTime = sinceTime;
    }

    public void setUntil(long until) {
      this.until = until;
    }
  }

}
