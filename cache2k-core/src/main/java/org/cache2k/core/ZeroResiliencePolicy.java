package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.CacheEntry;
import org.cache2k.io.LoadExceptionInfo;
import org.cache2k.io.ResiliencePolicy;

/**
 * Do not cache or suppress exceptions.
 *
 * @author Jens Wilke
 */
public class ZeroResiliencePolicy<K, V> implements ResiliencePolicy<K, V> {

  public static final ResiliencePolicy INSTANCE = new ZeroResiliencePolicy();

  @Override
  public long suppressExceptionUntil(K key, LoadExceptionInfo loadExceptionInfo,
                                     CacheEntry<K, V> cachedContent) {
    return 0;
  }

  @Override
  public long retryLoadAfter(K key, LoadExceptionInfo loadExceptionInfo) {
    return 0;
  }

}
