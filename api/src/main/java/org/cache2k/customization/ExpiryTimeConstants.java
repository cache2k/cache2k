package org.cache2k.customization;

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
 * Time values that have a special meaning. Used for expressive return values in the
 * customizations {@link org.cache2k.integration.ResiliencePolicy} and {@link ExpiryCalculator}.
 *
 * @author Jens Wilke
 */
public interface ExpiryTimeConstants {

  /**
   * Return value used to signal that the value should not be cached. In a read through
   * configuration the value will be loaded, when it is requested again.
   */
  long NO_CACHE = 0;

  /**
   * Return value signalling to keep the value forever in the cache, switching off expiry.
   * If the cache has a static expiry time configured, then this is used instead.
   */
  long ETERNAL = Long.MAX_VALUE;

}
