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

import org.cache2k.customization.ExpiryCalculator;

/**
 * Calculates the time when the object needs to be updated next.
 * 
 * @author Jens Wilke; created: 2010-06-24
 * @deprecated use {@link ExpiryCalculator}
 */
public abstract interface RefreshController<T> {

  /**
   * Returns the time of next refresh (expiry time) in milliseconds since epoch.
   * If 0 is returned, this means entry expires immediately, or is always
   * fetched from the source. If {@link Long#MAX_VALUE} is returned it means
   * there is no specific expiry time known or needed. In case a reasonable
   * default can be assumed for the expiry, the cache will use the
   * configured expiry time.
   *
   * <p>The cache may call the method a second (or more) times, if the
   * expiry time needs a recalculation. The reason for this is to react on
   * possible configuration changes properly. This may happen when an entry
   * is read back from storage.
   *
   * @param _oldObject the value currently in the cache. null if it is not
   *                   in the cache, is a null value (null is supported for values)
   *                   or the previous fetch operation yielded in an exception.
   * @param _timeOfLastRefresh time of the last cache refresh, by put or from the cache source.
   * @param _newObject the value which will be put in the cache.
   * @param _fetchTime this is the current time in millis. If a cache source was used to
   *            fetch the value, this is the time before the fetch was started.
   * @return time of next refresh in millis. 0 if it should not be cached at all.
   */
  public abstract long calculateNextRefreshTime(
    T _oldObject,
    T _newObject,
    long _timeOfLastRefresh,
    long _fetchTime);

}
