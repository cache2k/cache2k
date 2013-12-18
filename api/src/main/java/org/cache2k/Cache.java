
package org.cache2k;

/*
 * #%L
 * cache2k api only package
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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
 * Interface to the cache2k cache implementation. To obtain a cache
 * instance use the {@link CacheBuilder}
 *
 * @see CacheBuilder
 * @author Jens Wilke
 */
public interface Cache<K, T> extends KeyValueSource<K,T> {

  public abstract String getName();

  /**
   * Clear the cache contents
   */
  public abstract void clear();

  /**
   * Returns object mapped to key
   */
  public abstract T get(K key);

  /**
   * Retrieve an object the same way as get() would do. If there is a valid
   * object in the cache or a request carried out for the same object, return
   * immediately.
   */
  public abstract void prefetch(K key);

  /**
   * Returns the value if it is mapped within the cache.
   * No request on the cache source is made.
   */
  public abstract T peek(K key);

  /**
   * Set object value for the key
   */
  public abstract void put(K key, T value);

  /**
   * Remove the object mapped to key from the cache.
   */
  public abstract void remove(K key);

  /**
   * Free all reasources and deregister the cache from the cache manager.
   */
  public abstract void destroy();

}
