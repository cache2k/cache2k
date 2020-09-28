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

import org.cache2k.Cache2kBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Interface to add to a value object if it is possible to derive the
 * expiry time from the value. If no explicit expiry calculator is set
 * and this interface is detected on the value, the expiry requested
 * from the value by the cache.
 *
 * <p>Important caveat: This interface must be present on the configured cache value type to enable
 * the functionality.
 *
 * @author Jens Wilke
 */
public interface ValueWithExpiryTime {

  /**
   * Point in time in milliseconds when the value should expire.
   *
   * @return time of expiry in millis since epoch. See {@link ExpiryTimeValues} for the meaning of
   *          special values.
   * @see ExpiryPolicy
   * @see ExpiryTimeValues
   */
  long getCacheExpiryTime();

}
