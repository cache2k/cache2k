package org.cache2k.test.util;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.io.CacheLoader;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
public abstract class CacheSourceForTesting<K, V> implements CacheLoader<K, V> {

  private final AtomicInteger callCount = new AtomicInteger();

  protected final void incrementLoadCalledCount() {
    callCount.incrementAndGet();
  }

  public final int getLoaderCalledCount() {
    return callCount.intValue();
  }

}
