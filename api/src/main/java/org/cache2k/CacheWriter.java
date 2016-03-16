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
 * Writer for write-through configurations. Any mutation of the cache via the
 * {@link Cache}  interface, e.g.  {@link Cache#put(Object, Object)} or
 * {@link Cache#remove(Object)}  will cause a writer call.
 *
 * @author Jens Wilke; created: 2015-04-24
 */
public abstract class CacheWriter<K, V> {

  /**
   * Called when the value was updated or inserted into the cache.
   *
   * @param key key of the value to be written, never null.
   * @param value the value to be written, may be null if null is permitted.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public abstract void write(K key, V value) throws Exception;

  /**
   * Called when a mapping is removed from the cache. The removal was done by
   * {@link Cache#remove} or {@link Cache#removeAll()}. An expiry does not trigger a call
   * to this method.
   *
   * @param key key of the value removed from the cache, never null.
   * @throws Exception if an exception occurs, the cache update will not occur and this
   *         exception will be wrapped in a {@link CacheWriterException}
   */
  public abstract void delete(K key) throws Exception;

}
