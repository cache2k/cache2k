package org.cache2k.customization;

/*
 * #%L
 * cache2k API only package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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
