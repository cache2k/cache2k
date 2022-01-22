package org.cache2k.addon;

/*-
 * #%L
 * cache2k addon
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.annotation.Nullable;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.Feature;
import org.cache2k.io.ResiliencePolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.addon.UniversalResiliencePolicy.supplier;

/**
 * @author Jens Wilke
 */
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

  @AfterEach
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
          assertThat(b.config().getRetryInterval()).isEqualTo(ofMillis(1234));
          assertThat(b.config().getResilienceDuration()).isEqualTo(ofMillis(123));
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
      .expireAfterWrite(0, MINUTES)
      /* ... set loader ... */
      .build();
    assertThat(policy).isNull();
    cache.put(1, 123);
    assertThat((long) cache.invoke(1, e -> e.getExpiryTime())).isEqualTo(0);
    assertThat(cache.containsKey(1)).isFalse();
  }

  @Test
  public void enableDisable() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .setup(ResiliencePolicy::disable)
      .build();
    assertThat(policy).isNull();
  }

  @Test
  public void enableDisableViaResilience0() {
    cache = builder()
      .setup(UniversalResiliencePolicy::enable)
      .with(UniversalResilienceConfig.class, builder -> builder
        .resilienceDuration(0, MILLISECONDS)
      )
      .build();
    assertThat(policy).isNull();
  }

  /**
   * If no {@link Cache2kBuilder#expireAfterWrite(long, TimeUnit)} is set
   * resilience is not enabled even if the policy is added.
   */
  @Test
  public void noExpiry_noResilienceParameters() {
    cache = builder()
      .set(cfg -> cfg.setResiliencePolicy(supplier()))
      /* ... set loader ... */
      .build();
    assertThat(policy).isNull();
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
        .resilienceDuration(30, SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(3));
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
      .expireAfterWrite(10, MINUTES)
      /* ... set loader ... */
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(MINUTES.toMillis(10));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(MINUTES.toMillis(10));
    assertThat(policy.getRetryInterval()).isEqualTo(MINUTES.toMillis(1));
  }

  @Test
  public void expiry10m_duration30s() {
    cache = builder()
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .resilienceDuration(30, SECONDS)
      )
      .expireAfterWrite(10, MINUTES)
      .loader(this::load)
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(3));
    long t0 = currentTimeMillis();
    cache.get(1);
    assertThat((long) cache.invoke(1, e -> e.getExpiryTime()))
      .isGreaterThanOrEqualTo(t0 + MINUTES.toMillis(10));
    assertThat((long) cache.invoke(2, e ->
      {
        e.getValue();
        return e.getExpiryTime();
      }))
      .isGreaterThanOrEqualTo(t0 + MINUTES.toMillis(10));
    nextLoadThrowsException = true;
    assertThat((long) cache.invoke(3, e ->
      {
        e.getException();
        return e.getExpiryTime();
      }))
      .as("Retry")
      .isGreaterThanOrEqualTo(t0 + SECONDS.toMillis(3));
    assertThat((long) cache.invoke(2, e ->
      {
        e.load();
        assertThat((int) e.getValue()).isEqualTo(2);
        return e.getExpiryTime();
      }))
      .as("Suppress is doing retry as well")
      .isGreaterThanOrEqualTo(t0 + SECONDS.toMillis(3));
  }

  @Test
  public void expiry10m_retry10s() {
    cache = builder()
      .expireAfterWrite(10, MINUTES)
      .setupWith(UniversalResiliencePolicy::enable, b -> b
        .retryInterval(10, SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(MINUTES.toMillis(10));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(10));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(10));
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
        .resilienceDuration(30, SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(3));
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
        .resilienceDuration(30, SECONDS)
        .retryInterval(10, SECONDS)
      )
      .loader(this::load)
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(30));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(10));
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
        .retryInterval(10, SECONDS)
      )
      /* ... set loader ... */
      .build();
    assertThat(policy).isNotNull();
    assertThat(policy.getResilienceDuration()).isEqualTo(0);
    assertThat(policy.getMaxRetryInterval()).isEqualTo(SECONDS.toMillis(10));
    assertThat(policy.getRetryInterval()).isEqualTo(SECONDS.toMillis(10));
  }

  public static class ProbingException extends RuntimeException { }

}
