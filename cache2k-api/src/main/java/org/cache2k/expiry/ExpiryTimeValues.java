package org.cache2k.expiry;

/*
 * #%L
 * cache2k API
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

/**
 * Expiry time values that have a special meaning. Used for expressive return values in the
 * customizations {@link org.cache2k.integration.ResiliencePolicy} and {@link ExpiryPolicy}
 * as well as {@link org.cache2k.Cache#expireAt(Object, long)}.
 *
 * <p>Users may want to use the class {@link Expiry} with additional utility methods as
 * alternative.
 *
 * @author Jens Wilke
 * @see ExpiryPolicy
 * @see org.cache2k.integration.ResiliencePolicy
 * @see org.cache2k.Cache#expireAt
 * @see Expiry
 */
public interface ExpiryTimeValues {

  /**
   * Don't change the expiry of the entry. This can be used for an update.
   */
  long NEUTRAL = -1;

  /**
   * Value should not be cached. In a read through configuration the value will be
   * reloaded, when it is requested again.
   */
  long NO_CACHE = 0;

  /**
   * Identical to {@link #NO_CACHE}. More meaningful when used together with
   * {@link org.cache2k.Cache#expireAt}. The value expires immediately. An immediate
   * load is triggered if refreshAhead is enabled.
   */
  long NOW = 0;

  /**
   * An immediate load is triggered if refreshAhead is enabled. If the refresh is not
   * possible, for example because of no loader threads are available the value will expire.
   *
   * <p>After the load operation is completed, the entry is in a special area and not accessible
   * by direct cache operations, meaning {@code containsKey} returns false. After an operation which
   * would regularly trigger a load (e.g. {@code get} or {@code loadAll}), the entry is present in the cache.
   */
  long REFRESH = 1;

  /**
   * Return value signalling to keep the value forever in the cache, switching off expiry.
   * If the cache has a static expiry time configured, then this is used instead.
   */
  long ETERNAL = Long.MAX_VALUE;

}
