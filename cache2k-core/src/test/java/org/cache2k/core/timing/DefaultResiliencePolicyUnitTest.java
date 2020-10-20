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

import org.cache2k.Cache2kBuilder;
import org.cache2k.core.DefaultResiliencePolicy;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

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
    DefaultResiliencePolicy p = policy(builder());
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
    DefaultResiliencePolicy p = policy(builder().expireAfterWrite(100000, TimeUnit.MILLISECONDS));
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
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .resilienceDuration(30000, TimeUnit.MILLISECONDS)
    );
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  private static Cache2kBuilder builder() {
    return Cache2kBuilder.forUnknownTypes();
  }

  private static DefaultResiliencePolicy policy(final Cache2kBuilder builder) {
    DefaultResiliencePolicy policy = new DefaultResiliencePolicy(builder.toConfiguration());
    return policy;
  }

  /**
   * Expiry 240s, resilience duration 30s.
   */
  @Test
  public void testWithExpiryAndResilienceDuration10Min30Sec() {
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .resilienceDuration(30, TimeUnit.SECONDS)
    );
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, retry interval 10s.
   */
  @Test
  public void testWithExpiryAndRetryInterval() {
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .retryInterval(10000, TimeUnit.MILLISECONDS)
    );
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, max retry interval 10s.
   */
  @Test
  public void testWithExpiryAndMaxRetryInterval() {
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .maxRetryInterval(10000, TimeUnit.MILLISECONDS)
    );
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  @Test
  public void testRandomization() {
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .retryInterval(100, TimeUnit.MILLISECONDS)
      .maxRetryInterval(500, TimeUnit.MILLISECONDS)
      .resilienceDuration(5000, TimeUnit.MILLISECONDS)
    );
    p.setRandomization(0.5);
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
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .retryInterval(100, TimeUnit.MILLISECONDS)
      .maxRetryInterval(500, TimeUnit.MILLISECONDS)
      .resilienceDuration(5000, TimeUnit.MILLISECONDS)
    );
    p.setRandomization(0.0);
    assertEquals(0, p.getRandomization(), 0.1);
    p.setMultiplier(2);
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
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .retryInterval(100, TimeUnit.MILLISECONDS)
      .maxRetryInterval(500, TimeUnit.MILLISECONDS)
      .resilienceDuration(5000, TimeUnit.MILLISECONDS)
    );
    p.setRandomization(0.0);
    assertEquals(1.5, p.getMultiplier(), 0.1);
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
    DefaultResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .retryInterval(100, TimeUnit.MILLISECONDS)
      .maxRetryInterval(500, TimeUnit.MILLISECONDS)
      .resilienceDuration(5000, TimeUnit.MILLISECONDS)
    );
    p.setRandomization(0.0);
    assertEquals(1.5, p.getMultiplier(), 0.1);
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

  static class InfoBean implements LoadExceptionInfo {

    int retryCount;
    Throwable exception;
    long loadTime;
    long sinceTime;
    long until;

    @Override
    public Object getKey() { return null; }

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
