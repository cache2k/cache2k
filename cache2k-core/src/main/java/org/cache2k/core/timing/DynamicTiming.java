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

import org.cache2k.CacheEntry;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.api.InternalCacheCloseContext;
import org.cache2k.core.Entry;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;

/**
 * Timing handler with expiry policy.
 *
 * @author Jens Wilke
 */
class DynamicTiming<K, V> extends StaticTiming<K, V> {

  private final ExpiryPolicy<K, V> expiryPolicy;

  DynamicTiming(InternalCacheBuildContext<K, V> buildContext) {
    super(buildContext);
    expiryPolicy = constructPolicy(buildContext);
  }

  @SuppressWarnings("unchecked")
  private static <K, V> ExpiryPolicy<K, V> constructPolicy(InternalCacheBuildContext<K, V> buildContext) {
    Cache2kConfiguration<K, V> cfg = buildContext.getConfiguration();
    if (cfg.getValueType() != null &&
      ValueWithExpiryTime.class.isAssignableFrom(cfg.getValueType().getType()) &&
      cfg.getExpiryPolicy() == null) {
      return (ExpiryPolicy<K, V>) ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
    }
    return buildContext.createCustomization(cfg.getExpiryPolicy());
  }

  long calcNextRefreshTime(K key, V newObject, long now, CacheEntry<K, V> entry) {
    return calcNextRefreshTime(
      key, newObject, now, entry,
      expiryPolicy, expiryMillis, sharpExpiry);
  }

  public long calculateNextRefreshTime(Entry<K, V> entry, V newValue, long loadTime) {
    if (entry.isDataAvailable() || entry.isExpiredState()
      || entry.getNextRefreshTime() == Entry.EXPIRED_REFRESH_PENDING) {
      return calcNextRefreshTime(entry.getKey(), newValue, loadTime, entry.getTempCacheEntry());
    } else {
      return calcNextRefreshTime(entry.getKey(), newValue, loadTime, null);
    }
  }

  @Override
  public synchronized void close(InternalCacheCloseContext closeContext) {
    super.cancelAll();
    closeContext.closeCustomization(expiryPolicy, "expiryPolicy");
  }

}
