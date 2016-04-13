package org.cache2k.customization;

/*
 * #%L
 * cache2k API only package
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

import org.cache2k.Cache2kBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Special expiry calculator to calculate a separate expiry time if an
 * exception was thrown in the cache source. The general idea is that
 * depending on the nature of the exception (e.g. temporary or permanent)
 * a different expiry value can used.
 *
 * @author Jens Wilke; created: 2014-10-16
 */
public interface ExceptionExpiryCalculator<K> {

  /**
   * Returns the time of expiry for an exception in milliseconds since epoch.
   *
   * @param key the cache key used for inserting or loading
   * @param loadTime The time the entry was inserted or loaded. If a loader was used,
   *                 this is the time before the loader was called.
   * @return time the time of expiry in millis since epoch. {@link ExpiryCalculator#NO_CACHE} if it should not cached.
   *              {@link ExpiryCalculator#ETERNAL} if there is no specific expiry time known or needed.
   *              The effective expiry duration will never be longer than the
   *              configured expiry value via {@link Cache2kBuilder#expiryDuration(long, TimeUnit)}.
   *              If a negative value is returned, the negated value will be the expiry time
   *              used, but sharp expiry is requested always,
   *              ignoring {@link Cache2kBuilder#sharpExpiry(boolean)}.
   *
   * @see ExpiryCalculator#calculateExpiryTime
   */
  long calculateExpiryTime(K key, Throwable throwable, long loadTime);

}
