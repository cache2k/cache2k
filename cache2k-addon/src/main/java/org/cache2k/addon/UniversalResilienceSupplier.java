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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.config.WithSection;
import org.cache2k.io.ResiliencePolicy;

import java.time.Duration;

import static org.cache2k.config.Cache2kConfig.ETERNAL_DURATION;
import static org.cache2k.config.Cache2kConfig.UNSET_LONG;

/**
 * Supplier for a {@link UniversalResiliencePolicy}. The supplier can be used to
 * enable resilience and derive useful values from the basic cache configuration.
 *
 * @author Jens Wilke
 */
public class UniversalResilienceSupplier<K, V> implements
  CustomizationSupplier<ResiliencePolicy<K, V>>,
  WithSection<UniversalResilienceConfig, UniversalResilienceConfig.Builder> {

  private static final UniversalResilienceConfig DEFAULT_CONFIG = new UniversalResilienceConfig();

  @Override
  public ResiliencePolicy<K, V> supply(CacheBuildContext buildContext) {
    Cache2kConfig<K, V> rootCfg = buildContext.getConfig();
    return supplyPolicy(rootCfg);
  }

  static <K, V> ResiliencePolicy<K, V> supplyPolicy(Cache2kConfig<K, V> rootCfg) {
    UniversalResilienceConfig cfg =
      rootCfg.getSections().getSection(UniversalResilienceConfig.class, DEFAULT_CONFIG);
    long resilienceDuration = toMillis(cfg.getResilienceDuration());
    long retryInterval = toMillis(cfg.getRetryInterval());
    Duration expireAfterWrite = rootCfg.getExpireAfterWrite();
    long maxRetryInterval = toMillis(cfg.getMaxRetryInterval());
    if (resilienceDuration == UNSET_LONG) {
      if (expireAfterWrite == ETERNAL_DURATION) {
        resilienceDuration = 0;
      } else {
        if (expireAfterWrite != null) {
          resilienceDuration = expireAfterWrite.toMillis();
        } else {
          resilienceDuration = UNSET_LONG;
        }
      }
    } else {
      if (maxRetryInterval == UNSET_LONG) {
        maxRetryInterval = resilienceDuration;
      }
    }
    if (maxRetryInterval == UNSET_LONG && retryInterval == UNSET_LONG) {
      maxRetryInterval = resilienceDuration;
    }
    if (retryInterval == UNSET_LONG) {
      retryInterval = resilienceDuration * cfg.getRetryPercentOfResilienceDuration() / 100;
      retryInterval = Math.min(retryInterval, maxRetryInterval);
      if (cfg.getMinRetryInterval() != null) {
        retryInterval = Math.max(cfg.getMinRetryInterval().toMillis(), retryInterval);
      }
    }
    if (retryInterval > maxRetryInterval) {
      maxRetryInterval = retryInterval;
    }
    if (maxRetryInterval > resilienceDuration && resilienceDuration != 0) {
      resilienceDuration = maxRetryInterval;
    }
    if (resilienceDuration == 0 && retryInterval == 0) {
      return ResiliencePolicy.disabledPolicy();
    }
    UniversalResiliencePolicy<K, V> policy =
      new UniversalResiliencePolicy<>(
        resilienceDuration, maxRetryInterval, retryInterval,
        cfg.getBackoffMultiplier());
    return policy;
  }

  /**
   * Convert duration to millis. Duration is capped to Long.MAX_VALUE by config.
   */
  private static long toMillis(Duration d) {
    if (d == null) {
      return UNSET_LONG;
    }
    return d.toMillis();
  }

  @Override
  public Class<UniversalResilienceConfig> getConfigClass() {
    return UniversalResilienceConfig.class;
  }

}
