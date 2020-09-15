package org.cache2k.core.timing;

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

import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.configuration.CustomizationReferenceSupplier;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.core.Entry;
import org.cache2k.core.InternalCache;
import org.cache2k.core.util.InternalClock;
import org.cache2k.expiry.ExpiryPolicy;
import org.cache2k.expiry.ValueWithExpiryTime;

/**
 * Timing handler with expiry policy.
 *
 * @author Jens Wilke
 */
class DynamicTiming<K, V> extends StaticTiming<K, V> {

  private ExpiryPolicy<K, V> expiryPolicy;

  /**
   * Store policy factory until init is called and we have the cache reference
   */
  private CustomizationSupplier<ExpiryPolicy<K, V>> policyFactory;

  DynamicTiming(InternalClock c, Cache2kConfiguration<K, V> cc) {
    super(c, cc);
  }

  void configure(Cache2kConfiguration<K, V> c) {
    super.configure(c);
    policyFactory = c.getExpiryPolicy();
    if (policyFactory instanceof CustomizationReferenceSupplier) {
      try {
        expiryPolicy = policyFactory.supply(null);
      } catch (Exception ignore) {
      }
    }
    if (c.getValueType() != null &&
      ValueWithExpiryTime.class.isAssignableFrom(c.getValueType().getType()) &&
      c.getExpiryPolicy() == null) {
      expiryPolicy =
        (ExpiryPolicy<K, V>)
          ENTRY_EXPIRY_CALCULATOR_FROM_VALUE;
    }
  }

  @Override
  public synchronized void init(InternalCache<K, V> c) {
    super.init(c);
    if (expiryPolicy == null) {
      expiryPolicy = c.createCustomization(policyFactory);
    }
    policyFactory = null;
  }

  long calcNextRefreshTime(K key, V newObject, long now, Entry entry) {
    return calcNextRefreshTime(
      key, newObject, now, entry,
      expiryPolicy, expiryMillis, sharpExpiry);
  }

  public long calculateNextRefreshTime(Entry<K, V> entry, V newValue, long loadTime) {
    long t;
    if (entry.isDataAvailable() || entry.isExpiredState()
      || entry.getNextRefreshTime() == Entry.EXPIRED_REFRESH_PENDING) {
      t = calcNextRefreshTime(entry.getKey(), newValue, loadTime, entry);
    } else {
      t = calcNextRefreshTime(entry.getKey(), newValue, loadTime, null);
    }
    return t;
  }

  @Override
  public synchronized void close() {
    super.cancelAll();
    cache.closeCustomization(expiryPolicy, "expiryPolicy");
  }

}
