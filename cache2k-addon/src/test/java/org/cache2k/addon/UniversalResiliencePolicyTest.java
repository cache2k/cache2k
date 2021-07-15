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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.annotation.Nullable;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.Feature;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class UniversalResiliencePolicyTest {

  @Nullable Cache<Integer, Integer> cache;
  @Nullable UniversalResiliencePolicy<?, ?> policy;
  boolean nextLoadThrowsException;

  int load(int k) {
    if (nextLoadThrowsException) {
      throw new ProbingException();
    }
    return k;
  }

  @After
  public void tearDown() {
    if (cache != null) {
      cache.close();
    }
  }

  private Cache2kBuilder<Integer, Integer> builder() {
    Feature ft = new Feature() {
      @Override
      public <K, V> void enlist(CacheBuildContext<K, V> ctx) {
        CustomizationSupplier<? extends ResiliencePolicy> configuredSupplier =
          ctx.getConfig().getResiliencePolicy();
        if (configuredSupplier != null) {
          ctx.getConfig().setResiliencePolicy(buildContext -> {
            ResiliencePolicy policy = configuredSupplier.supply(buildContext);
            if (policy instanceof UniversalResiliencePolicy) {
              UniversalResiliencePolicyTest.this.policy = (UniversalResiliencePolicy<?, ?>) policy;
            }
            return policy;
          });
        }
      }
    };
    return new Cache2kBuilder<Integer, Integer>() { }
      .setup(b -> b.config().getFeatures().add(ft));
  }

  /* Set supplier and add config section in two commands */
  @Test
  public void configVariant_setSupplier() {
    cache = builder()
      .set(cfg -> cfg.setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .with(UniversalResilienceConfig.class, builder -> builder
        .resilienceDuration(0, TimeUnit.MILLISECONDS)
      )
      .build();
  }

  /* An exhausting set of alternatives to enable the policy and disable it. */
  @Test
  public void configVariant_enableDisable() {
    cache = builder()
      .set(cfg -> cfg.setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .setup(b -> b.config().setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .setup(UniversalResiliencePolicy::enable)
      .setup(ResiliencePolicy::disable)
      .build();
  }

  /* enable and add config section in single command */
  @Test
  public void configVariant_singleApply() {
    cache = builder()
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(4711, TimeUnit.MILLISECONDS)
      )
      .build();
  }

  /**
   * Enable the resilience in a global section, set some defaults and
   * overwrite the values again later.
   */
  @Test
  public void configVariant_overwrites() {
      cache = builder()
        .setupWith(UniversalResiliencePolicy::enable, b -> b
          .resilienceDuration(4711, TimeUnit.MILLISECONDS)
          .retryInterval(1234, TimeUnit.MILLISECONDS)
        )
        /* Overwrite the default later ...  */
        .with(UniversalResilienceConfig.class, builder -> builder
          .resilienceDuration(123, TimeUnit.MILLISECONDS)
        )
        /* Results in combined section parameters */
        .with(UniversalResilienceConfig.class, b -> {
          assertEquals(Duration.ofMillis(1234), b.config().getRetryInterval());
          assertEquals(Duration.ofMillis(123), b.config().getResilienceDuration());
        })
        .build();
  }

  /**
   * If expiry is set to 0, everything expires immediately and nothing will be cached,
   * including exceptions. The resilience policy is not created, since it would have no effect.
   */
  @Test
  public void expiry0_any() {
    cache = new Cache2kBuilder<Integer, Integer>() { }
      .setup(UniversalResiliencePolicy::enable)
      .expireAfterWrite(0, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertNull(policy);
    cache.put(1, 123);
    assertEquals(0, (long) cache.invoke(1, e -> e.getExpiryTime()));
    assertFalse(cache.containsKey(1));
  }

  @Test
  public void enableDisable() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .setup(ResiliencePolicy::disable)
      .build();
    assertNull(policy);
  }

  @Test
  public void enableDisableViaResilience0() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .with(UniversalResilienceConfig.class, builder -> builder
        .resilienceDuration(0, TimeUnit.MILLISECONDS)
      )
      .build();
    assertNull(policy);
  }

  /**
   * If no {@link Cache2kBuilder#expireAfterWrite(long, TimeUnit)} is set
   * resilience is not enabled even if the policy is added.
   */
  @Test
  public void noExpiry_noResilienceParameters() {
    cache = builder()
      .set(cfg -> cfg.setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      /* ... set loader ... */
      .build();
    assertNull(policy);
  }

  /**
   * In case, for any reason, it is wanted that values are not cached, but exceptions
   * are, it is possible to specify an expiry policy with immediate expiry.
   */
  @Test
  public void expiryPolicy() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .expiryPolicy((key, value, loadTime, oldEntry) -> 0)
      .with(UniversalResilienceConfig.class, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), policy.getRetryInterval());
  }

  /**
   * Values expire after 10 minutes. Exceptions are suppressed for 10 minutes
   * as well, if possible. A retry attempt is made after 1 minute. If the cache
   * continuously receives exceptions, the retry intervals are
   * exponentially increased up to 10 minutes.
   */
  @Test
  public void expiry10m() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.MINUTES.toMillis(10), policy.getResilienceDuration());
    assertEquals(TimeUnit.MINUTES.toMillis(10), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.MINUTES.toMillis(1), policy.getRetryInterval());
  }

  @Test
  public void expiry10m_duration30s() {
    cache = builder()
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
      )
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .loader(this::load)
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), policy.getRetryInterval());
    long t0 = System.currentTimeMillis();
    cache.get(1);
    Assertions.assertThat((long) cache.invoke(1, e -> e.getExpiryTime()))
      .isGreaterThanOrEqualTo(t0 + TimeUnit.MINUTES.toMillis(10));
    Assertions.assertThat((long) cache.invoke(2, e ->
      {
        e.getValue();
        return e.getExpiryTime();
      }))
      .isGreaterThanOrEqualTo(t0 + TimeUnit.MINUTES.toMillis(10));
    nextLoadThrowsException = true;
    Assertions.assertThat((long) cache.invoke(3, e ->
      {
        e.getException();
        return e.getExpiryTime();
      }))
      .as("Retry")
      .isGreaterThanOrEqualTo(t0 + TimeUnit.SECONDS.toMillis(3));
    Assertions.assertThat((long) cache.invoke(2, e ->
      {
        e.load();
        assertEquals(2, (int) e.getValue());
        return e.getExpiryTime();
      }))
      .as("Suppress is doing retry as well")
      .isGreaterThanOrEqualTo(t0 + TimeUnit.SECONDS.toMillis(3));
  }

  @Test
  public void expiry10m_retry10s() {
    cache = builder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .retryInterval(10, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.MINUTES.toMillis(10), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getRetryInterval());
  }

  /**
   * Values do not expire. If a loader exception happens the exception
   * is propagated and the first retry is done after approximately (plus randomization)
   * 3 seconds. After the second try and the following exceptions the retry interval will
   * be increased after a maximum of 30 seconds is reached.
   *
   * <p>For a cached value, a load can be triggered by {@code reload()}. If an
   * exception happens in this case it is suppressed for 30 seconds. A first retry
   * is done after 3 seconds.
   */
  @Test
  public void eternal_duration30s() {
    cache = builder()
      .eternal(true)
      .setup(UniversalResiliencePolicy::enable)
      .with(UniversalResilienceConfig.class, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), policy.getRetryInterval());
  }

  /**
   * Values do not expire. If a loader exception happens the exception
   * is propagated and the first retry is done after approximately (plus randomization)
   * 10 seconds. After the second try and the following exceptions the retry interval will
   * be increased after a maximum of 30 seconds is reached.
   *
   * <p>For a cached value, a load can be triggered by {@code reload()}. If an
   * exception happens in this case it is suppressed for 30 seconds. A first retry
   * is done after 3 seconds.
   */
  @Test
  public void eternal_duration30s_retry10s() {
    cache = builder()
      .eternal(true)
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
        .retryInterval(10, TimeUnit.SECONDS)
      )
      .loader(this::load)
      .build();
    assertNotNull(policy);
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getRetryInterval());
  }

  /**
   * No exception suppression, because eternal. The only way that a reload can be triggered
   * is with a reload operation. In this case we do not want suppression, unless
   * specified explicitly.
   */
  @Test
  public void eternal_retry10s() {
    cache = builder()
      .eternal(true)
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .retryInterval(10, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertNotNull(policy);
    assertEquals(0, policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getRetryInterval());
  }

  public static class ProbingException extends RuntimeException { }

}
