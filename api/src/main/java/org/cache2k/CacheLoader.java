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
 * @author Jens Wilke
 */
public interface CacheLoader<K, V> extends CacheSource<K,V>{

  /**
   *
   *
   * <p>API rationale: This method declares an exception to allow any unhandled
   * exceptions of the loader implementation to just pass through. Since the cache
   * needs to catch an deal with loader exceptions in any way, this saves otherwise
   * necessary try/catch clauses in the loader.
   *
   * @param key
   * @return value to be associated with the key. If the cache permits null values
   *         a null is associated with the key.
   *         TODO: If the cache does not permit null values, remove mapping as in JSR107?
   * @throws Exception Unhandled exception from the loader. The exception will be
   *         handled by the cache based on the configuration.
   */
  V get(K key) throws Exception;

}
