package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.CacheException;

/**
 * Used to rethrow if we get an {@link InterruptedException}
 *
 * @author Jens Wilke
 */
public class CacheOperationInterruptedException extends CacheException {

  /**
   * Set interrupt state again and propagate exception as {@link CacheOperationInterruptedException}
   */
  public static void propagate(InterruptedException ex) {
    Thread.currentThread().interrupt();
    throw new CacheOperationInterruptedException(ex);
  }

  public CacheOperationInterruptedException(InterruptedException original) {
    super(original);
  }
}
