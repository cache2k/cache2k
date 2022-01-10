package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

  private final Map<K, S> map;

  public MapValueConverterProxy(Map<K, S> map) {
    this.map = map;
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
  public boolean containsKey(Object key) {
    return map.containsKey(key);
  }

  @Override
  public boolean containsValue(Object value) {
    return values().contains(value);
  }

  @Override
  public V get(Object key) {
    S v = map.get(key);
    if (v == null) {
      return null;
    }
    return convert(v);
  }

  @Override
  public V put(K key, V value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public V remove(Object key) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
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
        Iterator<S> it = map.values().iterator();
        return new Iterator<V>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public V next() {
            return convert(it.next());
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
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
        Iterator<Entry<K, S>> it = map.entrySet().iterator();
        return new Iterator<Entry<K, V>>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public Entry<K, V> next() {
            Entry<K, S> e = it.next();
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
              public V setValue(V value) {
                throw new UnsupportedOperationException();
              }
            };
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
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
