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
   * @return time the time of expiry in millis since epoch. {@link ExpiryCalculator#NO_CACHE} if it
   *              should not be cached. {@link ExpiryCalculator#ETERNAL} means there is no specific
   *              expiry time known or needed. In any case the effective expiry duration will never be
   *              longer than the configured expiry value. If a negated value of the expiry time is returned,
   *              this means that sharp expiry is requested explicitly.
   *
   * @see ExpiryCalculator#calculateExpiryTime
   */
  long calculateExpiryTime(K key, Throwable throwable, long loadTime);

}
