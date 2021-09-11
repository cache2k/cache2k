package org.cache2k.core.timing;

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

import org.cache2k.CacheEntry;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.Entry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;
import org.cache2k.io.ResiliencePolicy;

/**
 * Timing handler with expiry policy.
 *
 * @author Jens Wilke
 */
class DynamicTiming<K, V> extends StaticTiming<K, V> {

  private final ExpiryPolicy<K, V> expiryPolicy;

  DynamicTiming(InternalCacheBuildContext<K, V> buildContext,
                ResiliencePolicy<K, V> resiliencePolicy) {
    super(buildContext, resiliencePolicy);
    expiryPolicy = constructPolicy(buildContext);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> ExpiryPolicy<K, V> constructPolicy(
    InternalCacheBuildContext<K, V> buildContext) {
    Cache2kConfig<K, V> cfg = buildContext.getConfig();
    if (cfg.getValueType() != null &&
      ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()) &&
      cfg.getExpiryPolicy() == null) {
      return (ExpiryPolicy<K, V>) ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
    }
    return (ExpiryPolicy<K, V>) buildContext.createCustomization(cfg.getExpiryPolicy());
  }

  long calcNextRefreshTime(K key, V value, long now, CacheEntry<K, V> entry) {
    return calcNextRefreshTime(
      key, value, now, entry,
      expiryPolicy, expiryMillis, sharpExpiry);
  }

  public long calculateNextRefreshTime(Entry<K, V> entry, V value, long loadTime) {
    if (entry.isDataAvailable() || entry.isExpiredState()
      || entry.getNextRefreshTime() == Entry.EXPIRED_REFRESH_PENDING) {
      return calcNextRefreshTime(entry.getKey(), value, loadTime,
        entry.getInspectionEntry());
    } else {
      return calcNextRefreshTime(entry.getKey(), value, loadTime, null);
    }
  }

  @Override
  public synchronized void close(InternalCacheCloseContext closeContext) {
    super.close(closeContext);
    closeContext.closeCustomization(expiryPolicy, "expiryPolicy");
  }

}
