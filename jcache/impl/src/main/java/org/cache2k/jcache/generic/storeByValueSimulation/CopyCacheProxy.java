package org.cache2k.jcache.generic.storeByValueSimulation;

/*
 * #%L
 * cache2k JCache JSR107 implementation
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Configuration;

/**
 * Cache proxy that expects the transformers keep the type but copy the objects. Copying could be
 * done e.g. cloning and serialization or can be skipped in case of immutable types.
 *
 * @author Jens Wilke
 */
public class CopyCacheProxy<K, T> extends TransformingCacheProxy<K, T, K, T> {

  @SuppressWarnings("unchecked")
  public CopyCacheProxy(Cache<K, T> cache, ObjectTransformer<K, K> keyTransformer, ObjectTransformer<T, T> valueTransformer) {
    super(cache, keyTransformer, valueTransformer, ObjectTransformer.IDENT_TRANSFORM, ObjectTransformer.IDENT_TRANSFORM);
  }

  /**
   * Delegates to the wrapped cache.
   */
  @Override
  public <C extends Configuration<K, T>> C getConfiguration(Class<C> clazz) {
    return cache.getConfiguration(clazz);
  }

  /**
   * Delegates to wrapped cache.
   */
  @Override
  public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, T> cacheEntryListenerConfiguration) {
    cache.registerCacheEntryListener(cacheEntryListenerConfiguration);
  }

  /**
   * Delegates to wrapped cache.
   */
  @Override
  public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, T> cacheEntryListenerConfiguration) {
    cache.deregisterCacheEntryListener(cacheEntryListenerConfiguration);
  }

}
