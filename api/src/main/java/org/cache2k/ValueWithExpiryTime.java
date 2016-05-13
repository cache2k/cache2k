package org.cache2k;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.expiry.ExpiryPolicy;

/**
 * Interface to add to a value object if it is possible to derive the
 * expiry time from the value. If no explicit expiry calculator is set
 * and this interface is detected on the value, the expiry requested
 * from the value by the cache.
 *
 * @author Jens Wilke; created: 2014-10-15
 * @since 0.20
 * @deprecated replaced with {@link org.cache2k.expiry.ValueWithExpiryTime}
 */
public interface ValueWithExpiryTime extends org.cache2k.expiry.ValueWithExpiryTime {

  /**
   * Instance of an expiry calculator that uses the expiry value from the value
   * object, or returns the maximum value, if the interface is not implemented
   * by the value. This is useful if a cache contains different types of objects
   * which may need expiry at a specified time or not.
   *
   * @since 0.20
   */
  ExpiryPolicy<?, ?> AUTO_EXPIRY = new ExpiryPolicy<Object, Object>() {
    @Override
    public long calculateExpiryTime(
        Object _key, Object _value, long _loadTime,
        CacheEntry<Object, Object> _oldEntry) {
      if (_value instanceof ValueWithExpiryTime) {
        return ((ValueWithExpiryTime) _value).getCacheExpiryTime();
      }
      return Long.MAX_VALUE;
    }
  };

  /**
   * Return time of next refresh (expiry time). A return value of 0 means the
   * entry expires immediately, or is always fetched from the source. A return value of
   * {@link Long#MAX_VALUE} means there is no specific expiry time
   * known or needed. In this case a reasonable default can be assumed for
   * the expiry, the cache will use the configured expiry time.
   *
   * @since 0.20
   */
  @Override
  long getCacheExpiryTime();

}
