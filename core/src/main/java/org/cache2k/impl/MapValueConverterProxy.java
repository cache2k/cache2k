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

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Delegates all requests to the given map and converts the value.
 * Mutation operation are unsupported.
 *
 * @author Jens Wilke
 */
public abstract class MapValueConverterProxy<K, V, S>  implements Map<K, V> {

  private Map<K, S> map;

  public MapValueConverterProxy(final Map<K, S> _map) {
    map = _map;
  }

  /**
   * Only called when the value object is requested. This may yield
   * in an exception.
   */
  protected abstract V convert(S v);

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return map.size();
  }

  @Override
  public boolean isEmpty() {
    return map.isEmpty();
  }

  @Override
  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(final Object value) {
    return values().contains(value);
  }

  @Override
  public V get(final Object key) {
    return convert(map.get(key));
  }

  @Override
  public V put(final K key, final V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V remove(final Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(final Map<? extends K, ? extends V> m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Set<K> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<V> values() {
    return new AbstractCollection<V>() {

      @Override
      public Iterator<V> iterator() {
        final Iterator<S> it = map.values().iterator();
        return new Iterator<V>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public V next() {
            return convert(it.next());
          }
        };
      }

      @Override
      public int size() {
        return map.size();
      }
    };
  }

  @Override
  public Set<Entry<K, V>> entrySet() {

    return new AbstractSet<Entry<K, V>>() {
      @Override
      public Iterator<Entry<K, V>> iterator() {
        final Iterator<Entry<K,S>> it = map.entrySet().iterator();
        return new Iterator<Entry<K, V>>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public Entry<K, V> next() {
            final Entry<K, S> e = it.next();
            return new Entry<K, V>() {
              @Override
              public K getKey() {
                return e.getKey();
              }

              @Override
              public V getValue() {
                return convert(e.getValue());
              }

              @Override
              public V setValue(final V value) {
                throw new UnsupportedOperationException();
              }
            };
          }
        };
      }

      @Override
      public int size() {
        return map.size();
      }
    };
  }

}
