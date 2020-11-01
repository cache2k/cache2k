package org.cache2k.addon;

/*
 * #%L
 * cache2k addon
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
import org.cache2k.CustomizationException;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class UniversalResiliencePolicyTest {

  Cache<Integer, Integer> cache;
  UniversalResiliencePolicy<?, ?> policy;

  @After
  public void tearDown() {
    if (cache != null) {
      cache.close();
    }
  }

  private Cache2kBuilder<Integer, Integer> builder() {
    return new Cache2kBuilder<Integer, Integer>() { }
      .configAugmenter((context, config) -> {
        CustomizationSupplier<? extends ResiliencePolicy> configuredSupplier =
          config.getResiliencePolicy();
        if (configuredSupplier != null) {
          config.setResiliencePolicy(buildContext -> {
            ResiliencePolicy policy = configuredSupplier.supply(buildContext);
            if (policy instanceof UniversalResiliencePolicy) {
              this.policy = (UniversalResiliencePolicy<?, ?>) policy;
            }
            return policy;
          });
        }
      });
  }

  /**
   * If expiry is set to 0, everything expires immediately and nothing will be cached,
   * including exceptions. The resilience policy is not created, since it would have no effect.
   */
  @Test
  public void expiry0_any() {
    cache = new Cache2kBuilder<Integer, Integer>() { }
      .apply(UniversalResiliencePolicy::enable)
      .expireAfterWrite(0, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertNull(policy);
  }

  @Test
  public void configVariants() {
    cache = builder()
      /* set supplier and add config section in two commands */
      .set(cfg -> cfg.setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .section(UniversalResilienceConfig.class, builder -> builder
        .resilienceDuration(0, TimeUnit.MILLISECONDS)
      )
      /* Exhausting set of alternatives to enable the policy. */
      .set(cfg -> cfg.setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .apply(b -> b.config().setResiliencePolicy(UniversalResiliencePolicy.supplier()))
      .apply(UniversalResiliencePolicy::enable)
      /* Let's disable it again. */
      .apply(ResiliencePolicy::disable)
      /* enable and add config section in single command */
      .apply(UniversalResiliencePolicy::enable, b -> b
          .resilienceDuration(4711, TimeUnit.MILLISECONDS)
       )
      /* maybe we enable the resilience in a global section and set some defaults ... */
      .apply(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(4711, TimeUnit.MILLISECONDS)
        .retryInterval(1234, TimeUnit.MILLISECONDS)
      )
      /* ... and overwrite the default later ...  */
      .section(UniversalResilienceConfig.class, builder -> builder
        .resilienceDuration(123, TimeUnit.MILLISECONDS)
      )
      /* ... results in combined section parameters */
      .section(UniversalResilienceConfig.class, b -> {
        assertEquals(1234, b.config().getRetryInterval().toMillis());
        assertEquals(123, b.config().getResilienceDuration().toMillis());
      })
      .build();
    assertNotNull(policy);
  }

  @Test
  public void enableDisable() {
    cache = builder()
      .apply(UniversalResiliencePolicy::enable)
      .apply(ResiliencePolicy::disable)
      .build();
    assertNull(policy);
  }

  @Test
  public void enableDisableViaResilience0() {
    cache = builder()
      .apply(UniversalResiliencePolicy::enable)
      .section(UniversalResilienceConfig.class, builder -> builder
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
      .apply(UniversalResiliencePolicy::enable)
      .expiryPolicy((key, value, loadTime, oldEntry) -> 0)
      .section(UniversalResilienceConfig.class, b -> b
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
      .apply(UniversalResiliencePolicy::enable)
      .expireAfterWrite(10, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertEquals(TimeUnit.MINUTES.toMillis(10), policy.getResilienceDuration());
    assertEquals(TimeUnit.MINUTES.toMillis(10), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.MINUTES.toMillis(1), policy.getRetryInterval());
  }

  @Test
  public void expiry10m_duration30s() {
    cache = builder()
      .apply(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
      )
      .expireAfterWrite(10, TimeUnit.MINUTES)
      /* ... set loader ... */
      .build();
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(3), policy.getRetryInterval());
  }

  @Test
  public void expiry10m_retry10s() {
    cache = builder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .apply(UniversalResiliencePolicy::enable, b -> b
        .retryInterval(10, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
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
      .apply(UniversalResiliencePolicy::enable)
      .section(UniversalResilienceConfig.class, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
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
      .apply(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(30, TimeUnit.SECONDS)
        .retryInterval(10, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(30), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getRetryInterval());
  }

  /**
   * No suppression, because eternal. The only way that a reload can be triggered
   * is with a reload operation. In this case we do not want suppression, unless
   * specified explicitly.
   */
  @Test
  public void eternal_retry10s() {
    cache = builder()
      .eternal(true)
      .apply(UniversalResiliencePolicy::enable, b -> b
        .retryInterval(10, TimeUnit.SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertEquals(0, policy.getResilienceDuration());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getMaxRetryInterval());
    assertEquals(TimeUnit.SECONDS.toMillis(10), policy.getRetryInterval());
  }

  @Test(expected = CustomizationException.class)
  public void noSuppress_duration10m() {
    Cache<Integer, Integer> c = new Cache2kBuilder<Integer, Integer>() { }
      .eternal(true)
      .apply(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(10, TimeUnit.MINUTES)
        .suppressExceptions(false)
      )
      /* ... set loader ... */
      .build();
  }

}
