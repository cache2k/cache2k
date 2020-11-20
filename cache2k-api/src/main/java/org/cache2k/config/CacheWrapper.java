package org.cache2k.config;

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

import org.cache2k.Cache;

/**
 * Wraps a cache in order to add functionality like tracing.
 * The cache wrapper is called after a cache is build and may wrap
 * a cache instance. The {@link org.cache2k.Cache2kBuilder@#build} and
 * {@link org.cache2k.CacheManager#getCache(String)} will return the wrapped
 * cache instead of the original. Depending on the configuration the wrapper
 * may also decide to just return the original cache instance.
 *
 * <p>The configuration allows to specify two different wrappers.
 * One for tracing {@link Cache2kConfig#setTraceCacheWrapper(CacheWrapper)} and
 * one for general use {@link Cache2kConfig#setCacheWrapper(CacheWrapper)}. The wrapping
 * is applied in that order, with tracing first.
 *
 * <p>To wrap a Cache the base classes {@link org.cache2k.AbstractCache}
 * or {@link org.cache2k.ForwardingCache} are available.
 *
 * @author Jens Wilke
 */
public interface CacheWrapper {

  <K, V> Cache<K, V> wrap(CacheBuildContext<K, V> context, Cache<K, V> cache);

}
