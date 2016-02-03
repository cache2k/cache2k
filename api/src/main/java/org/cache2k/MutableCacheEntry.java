package org.cache2k;

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
 * @author Jens Wilke; created: 2013-12-21
 */
public interface MutableCacheEntry<K, V> extends CacheEntry<K, V> {

  /**
   * <p>Returns the value to which the cache associated the key,
   * or {@code null} if the cache contained no mapping for the key.
   *
   * <p>If the cache does permit null values, then a return value of
   * {@code null} does not necessarily indicate that the cache
   * contained no mapping for the key. It is also possible that the cache
   * explicitly associated the key to the value {@code null}. Use {@link #exists()}
   * to check whether an entry is existing instead of a null check.
   *
   * <p>If read through operation is enabled and the entry not yet existing
   * in the cache, the call to this method triggers a call to the cache loader.
   *
   * <p>In contrast to the main Cache interface there is no no peekValue method,
   * since the same effect can be achieved by the combination of {@link #exists()}
   * and {@link #getValue()}.
   *
   * @throws RestartException if the value is not yet available and the cache loader
   *                          needs to be invoked. After the load the entry processor
   *                          will be executed again.
   * @see CacheLoader
   */
  @Override
  V getValue();

  /**
   * True if a mapping exists in the cache, never invokes the loader / cache source.
   *
   * @throws RestartException if the information about the entry is not yet available
   *                          and the cache needs to retrieve information from the
   *                          secondary storage. After completed, the entry processor
   *                          will be executed again.
   */
  boolean exists();

  void setValue(V v);
  void setException(Throwable ex);
  void remove();

}
