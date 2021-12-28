package org.cache2k.expiry;

/*
 * #%L
 * cache2k API
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

import org.cache2k.config.Cache2kConfig;
import org.cache2k.io.ResiliencePolicy;
import org.cache2k.processor.MutableCacheEntry;

/**
 * Expiry time values that have a special meaning. Used for expressive return values in the
 * customizations {@link ResiliencePolicy} and {@link ExpiryPolicy}
 * as well as {@link org.cache2k.Cache#expireAt(Object, long)}.
 *
 * <p>Users may want to use the class {@link Expiry} with additional utility methods as
 * alternative.
 *
 * @author Jens Wilke
 * @see ExpiryPolicy
 * @see ResiliencePolicy
 * @see org.cache2k.Cache#expireAt
 * @see org.cache2k.processor.MutableCacheEntry#setExpiryTime(long)
 * @see Expiry
 */
public interface ExpiryTimeValues {

  /**
   * Don't change the expiry of the entry. This can be used for an update.
   * This is also used for an undefined / not applicable expiry time value
   * in {@link MutableCacheEntry#getExpiryTime()}. The value is
   * identical to {@link Cache2kConfig#UNSET_LONG}
   */
  long NEUTRAL = Cache2kConfig.UNSET_LONG;

  /**
   * The value expires immediately and will not be cached. No refreshing is started.
   */
  long NOW = 0;

  /**
   * Expires the entry and starts a refresh if {@link org.cache2k.Cache2kBuilder#refreshAhead}
   * is enabled. With a positive value the current value is visible until the refresh is complete.
   * With a negative value a {@code get()} requests will block until the refreshed value
   * is available.
   *
   * <p>Rationale: Any value in the past does and between the current time, does
   * the same as if the time is reached just now.
   */
  long REFRESH = 1234;

  /**
   * Return value signalling to keep the value forever in the cache, switching off expiry.
   * If the cache has a static expiry time configured, then this is used instead.
   */
  long ETERNAL = Long.MAX_VALUE;

}
