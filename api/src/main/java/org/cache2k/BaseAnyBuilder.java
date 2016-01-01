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
 * Base builder which defines top level methods
 * delegating to the root configuration builder.
 *
 * @author Jens Wilke; created: 2014-04-19
 * @param <C> configuration bean type
 */
public abstract class BaseAnyBuilder<K, T, C>
  implements AnyBuilder<K, T, C> {

  protected CacheBuilder<K, T> root;

  BaseAnyBuilder() { }

  void setRoot(CacheBuilder<K, T> v) {
    root = v;
  }

  /**
   * Adds persistence to the cache or returns a previous added persistence storage
   * configuration node. The persistence configuration is taken from a default
   * configuration. Ideally speaking requesting persistence means that the
   * cache contains data that is costly to reproduce and/or needs a big
   * amount of storage which is not available within the java heap.
   */
  public StorageConfiguration.Builder<K, T, ?> persistence() { return root.persistence(); }

  public CacheBuilder<K, T> root() {
    return root;
  }

  public Cache<K, T> build() {
    return root.build();
  }

}
