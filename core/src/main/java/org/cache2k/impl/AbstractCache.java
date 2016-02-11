package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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

import org.cache2k.CacheEntry;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Some default implementations for a cache.
 *
 * @author Jens Wilke
 */
public abstract class AbstractCache<K, V> implements InternalCache<K, V> {

  @Override
  public void removeAllAtOnce(Set<K> _keys) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeAll() {
    for (CacheEntry<K, V> e : this) {
      remove(e.getKey());
    }
  }

  @Override
  public void removeAll(Set<? extends K> _keys) {
    for (K k : _keys) {
      remove(k);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <X> X requestInterface(Class<X> _type) {
    if (_type.equals(ConcurrentMap.class) ||
      _type.equals(Map.class)) {
      return (X) new ConcurrentMapWrapper<K, V>(this);
    }
    if (_type.equals(InternalCache.class)) {
      return (X) this;
    }
    return null;
  }

  @Override
  public StorageAdapter getStorage() { return null; }

  @Override
  public void flush() {

  }

  @Override
  public void purge() {

  }

}
