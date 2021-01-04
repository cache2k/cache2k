package org.cache2k.addon;

/*
 * #%L
 * cache2k addon
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.annotation.Nullable;
import org.cache2k.io.ExceptionPropagator;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Unit test with out cache for default resilience policy.
 *
 * @author Jens Wilke
 * @see UniversalResiliencePolicy
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Category(FastTests.class)
public class UniversalResiliencePolicyUnitTest {

  static final CacheEntry DUMMY_ENTRY = new CacheEntry() {
    @Override
    public Object getKey() {
      return this;
    }

    @Override
    public Object getValue() {
      return this;
    }

    @Override
    public @Nullable LoadExceptionInfo getExceptionInfo() {
      return null;
    }
  };

  static final ExceptionPropagator DUMMY_PROPAGATOR = new ExceptionPropagator() {
    @Override
    public RuntimeException propagateException(LoadExceptionInfo loadExceptionInfo) {
      return new RuntimeException();
    }
  };

  @Test
  public void testBackoffPower() {
    assertEquals(10, 10 * Math.pow(1.5, 0), 0.1);
    assertEquals(15, 10 * Math.pow(1.5, 1), 0.1);
    assertEquals(22.5, 10 * Math.pow(1.5, 2), 0.1);
    assertEquals(33.75, 10 * Math.pow(1.5, 3), 0.1);
  }

  /**
   * Suppress duration defaults to expiry if not set.
   */
  @Test
  public void testDefaultSuppressDuration() {
    UniversalResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(100000, p.getResilienceDuration());
  }

  private UniversalResiliencePolicy getDefaultResiliencePolicy10000() {
    UniversalResiliencePolicy p = policy(builder().expireAfterWrite(100000, TimeUnit.MILLISECONDS));
    Objects.requireNonNull(p);
    return p;
  }

  /**
   * Max retry interval is expiry time.
   */
  @Test
  public void testDefaultMaxRetryInterval() {
    UniversalResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(100000, p.getMaxRetryInterval());
  }

  /**
   * 10% of expireAfter or resilienceDuration write
   */
  @Test
  public void testDefaultRetryInterval() {
    UniversalResiliencePolicy p = getDefaultResiliencePolicy10000();
    assertEquals(10000, p.getRetryInterval());
  }

  /**
   * Expiry 240s, resilience duration 30s.
   */
  @Test
  public void testWithExpiryAndResilienceDuration() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .resilienceDuration(30000, TimeUnit.MILLISECONDS)
      )
    );
    assertNotNull(p);
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  private static Cache2kBuilder<?, ?> builder() {
    return Cache2kBuilder.forUnknownTypes();
  }

  private static @Nullable UniversalResiliencePolicy policy(Cache2kBuilder builder) {
    ResiliencePolicy policy = UniversalResilienceSupplier.supplyPolicy(builder.config());
    if (policy instanceof UniversalResiliencePolicy) {
      return (UniversalResiliencePolicy) policy;
    }
    return null;
  }

  /**
   * Expiry 240s, resilience duration 30s.
   */
  @Test
  public void testWithExpiryAndResilienceDuration10Min30Sec() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .with(
        UniversalResilienceConfig.class, b -> b
          .resilienceDuration(30, TimeUnit.SECONDS)
      )
    );
    assertNotNull(p);
    assertEquals(30000, p.getResilienceDuration());
    assertEquals(3000, p.getRetryInterval());
    assertEquals(30000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, retry interval 10s.
   */
  @Test
  public void testWithExpiryAndRetryInterval() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .retryInterval(10000, TimeUnit.MILLISECONDS)
      )
    );
    assertNotNull(p);
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  /**
   * Expiry 240s, max retry interval 10s.
   */
  @Test
  public void testWithExpiryAndMaxRetryInterval() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(240000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .maxRetryInterval(10000, TimeUnit.MILLISECONDS)
      )
    );
    assertNotNull(p);
    assertEquals(240000, p.getResilienceDuration());
    assertEquals(10000, p.getRetryInterval());
    assertEquals(10000, p.getMaxRetryInterval());
  }

  @Test
  public void testCustomMultiplier() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .retryInterval(100, TimeUnit.MILLISECONDS)
        .maxRetryInterval(500, TimeUnit.MILLISECONDS)
        .resilienceDuration(5000, TimeUnit.MILLISECONDS)
        .backoffMultiplier(2)
      )
    );
    assertNotNull(p);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(100, t);
    b.incrementRetryCount();
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(200, t);
  }

  @Test
  public void testSuppress() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .retryInterval(100, TimeUnit.MILLISECONDS)
        .maxRetryInterval(500, TimeUnit.MILLISECONDS)
        .resilienceDuration(5000, TimeUnit.MILLISECONDS)
      )
    );
    assertNotNull(p);
    assertEquals(1.5, p.getMultiplier(), 0.1);
    InfoBean b = new InfoBean();
    b.setLoadTime(0);
    b.setSinceTime(0);
    long t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(100, t);
    b.incrementRetryCount();
    b.setLoadTime(107);
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(150, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(300);
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(225, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(582);
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(337, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(934);
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(500, t - b.getLoadTime());
    b.incrementRetryCount();
    b.setLoadTime(1534);
    t = p.suppressExceptionUntil("key", b, DUMMY_ENTRY);
    assertEquals(500, t - b.getLoadTime());
  }

  @Test
  public void testCache() {
    UniversalResiliencePolicy p = policy(builder()
      .expireAfterWrite(10000, TimeUnit.MILLISECONDS)
      .with(UniversalResilienceConfig.class, b -> b
        .retryInterval(100, TimeUnit.MILLISECONDS)
        .maxRetryInterval(500, TimeUnit.MILLISECONDS)
        .resilienceDuration(5000, TimeUnit.MILLISECONDS)
      )
    );
    assertNotNull(p);
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

    private int retryCount;
    private Throwable exception = new Throwable();
    private long loadTime;
    private long sinceTime;
    private long until;

    @Override
    public Object getKey() { return this; }

    @Override
    public ExceptionPropagator getExceptionPropagator() { return DUMMY_PROPAGATOR; }

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
