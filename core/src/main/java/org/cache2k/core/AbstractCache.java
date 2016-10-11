package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.CacheEntry;
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.configuration.CustomizationFactory;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.storageApi.StorageAdapter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * Some default implementations for a cache.
 *
 * @author Jens Wilke
 */
public abstract class AbstractCache<K, V> implements InternalCache<K, V> {

  /**
   * Key iteration on top of normal iterator.
   */
  @Override
  public Iterable<K> keys() {
    return new Iterable<K>() {
      @Override
      public Iterator<K> iterator() {
        final Iterator<CacheEntry<K,V>> it = AbstractCache.this.iterator();
        return new Iterator<K>() {
          @Override
          public boolean hasNext() {
            return it.hasNext();
          }

          @Override
          public K next() {
            return it.next().getKey();
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  @Override
  public Iterable<CacheEntry<K, V>> entries() {
    return new Iterable<CacheEntry<K, V>>() {
      @Override
      public Iterator<CacheEntry<K, V>> iterator() {
        return AbstractCache.this.iterator();
      }
    };
  }

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
  public void removeAll(Iterable<? extends K> _keys) {
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
    if (_type.isAssignableFrom(this.getClass())) {
      return (X) this;
    }
    return null;
  }

  @Override
  public ConcurrentMap<K, V> asMap() {
    return new ConcurrentMapWrapper<K, V>(this);
  }

  @Override
  public StorageAdapter getStorage() { return null; }

  @Override
  public void flush() {

  }

  @Override
  public void purge() {

  }

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Iterable<? extends K> keys, EntryProcessor<K, V, R> entryProcessor) {
    Map<K, EntryProcessingResult<R>> m = new HashMap<K, EntryProcessingResult<R>>();
    for (K k : keys) {
      try {
        final R _result = invoke(k, entryProcessor);
        if (_result == null) {
          continue;
        }
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            return _result;
          }

          @Override
          public Throwable getException() {
            return null;
          }
        });
      } catch (EntryProcessingException t) {
        final Throwable _cause = t.getCause();
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            throw new EntryProcessingException(_cause);
          }

          @Override
          public Throwable getException() {
            return _cause;
          }
        });
      }
    }
    return m;
  }

  protected <R> R execute(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    EntryAction<K, V, R> _action = createEntryAction(key, e, op);
    return execute(op, _action);
  }

  protected abstract <R> EntryAction<K, V, R> createEntryAction(K key, Entry<K, V> e, Semantic<K, V, R> op);

  protected <R> R execute(Semantic<K, V, R> op, final EntryAction<K, V, R> _action) {
    op.start(_action);
    if (_action.entryLocked) {
      throw new CacheInternalError("entry not unlocked?");
    }
    RuntimeException t = _action.exceptionToPropagate;
    if (t != null) {
      t.fillInStackTrace();
      throw t;
    }
    return _action.result;
  }

  protected <R> R execute(K key, Semantic<K, V, R> op) {
    return execute(key, null, op);
  }

  @Override
  public void prefetch(final List<? extends K> keys, final int _startIndex, final int _endIndexExclusive) {
    prefetch(keys.subList(_startIndex, _endIndexExclusive));
  }

  @Override
  public void prefetch(final Iterable<? extends K> keys) {
    prefetchAll(keys);
  }

  @Override
  public StorageMetrics getStorageMetrics() {
    return StorageMetrics.DUMMY;
  }

  @Override
  public boolean contains(final K key) {
    return containsKey(key);
  }

  @Override
  public void prefetchAll(final CacheOperationCompletionListener _listener, final K... _keys) {
    prefetchAll(_listener, Arrays.asList(_keys));
  }

  @Override
  public <T> T createCustomization(final CustomizationFactory<T> f) {
    if (f == null) {
      return null;
    }
    try {
      return f.create(getCacheManager());
    } catch (Exception ex) {
      throw new CacheMisconfigurationException("Initialization of customization failed", ex);
    }
  }
}
