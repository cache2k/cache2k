package org.cache2k.core.experimentalApi;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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

import java.util.EventListener;
import java.util.concurrent.Executor;

/**
 * @author Jens Wilke
 */
public interface AsyncCacheLoader<K,V> {

  void get(K key, V value, CacheEntry<K, V> entry, Callback<V> callback, Executor ex);

  /**
   * Callback for async cache load.
   *
   * @author Jens Wilke
   */
  interface Callback<V> extends EventListener {

    void onLoadSuccess(V value);

    void onLoadFailure(Throwable t);

  }

}
