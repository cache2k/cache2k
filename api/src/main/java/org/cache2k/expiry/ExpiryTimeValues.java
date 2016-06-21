package org.cache2k.expiry;

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

/**
 * Expiry time values that have a special meaning. Used for expressive return values in the
 * customizations {@link org.cache2k.integration.ResiliencePolicy} and {@link ExpiryPolicy}
 * as well as {@link org.cache2k.Cache#expireAt(Object, long)}.
 *
 * @author Jens Wilke
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
   * If refresh ahead is enabled, the value will be cached and visible. An immediate
   * refresh is triggered. If the refresh is not possible, because no loader threads
   * are available the value will expire.
   */
  long REFRESH_IMMEDIATELY = 1;

  /**
   * Return value signalling to keep the value forever in the cache, switching off expiry.
   * If the cache has a static expiry time configured, then this is used instead.
   */
  long ETERNAL = Long.MAX_VALUE;

}
