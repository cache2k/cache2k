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
import org.cache2k.CacheEntryProcessor;
import org.cache2k.EntryProcessingResult;
import org.cache2k.WrappedCustomizationException;
import org.cache2k.impl.operation.Semantic;

import java.util.HashMap;
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

  @Override
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Set<? extends K> keys, CacheEntryProcessor<K, V, R> entryProcessor, Object... objs) {
    Map<K, EntryProcessingResult<R>> m = new HashMap<K, EntryProcessingResult<R>>();
    for (K k : keys) {
      try {
        final R _result = invoke(k, entryProcessor, objs);
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
      } catch (final Throwable t) {
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            return null;
          }

          @Override
          public Throwable getException() {
            return t;
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
    WrappedCustomizationException t = _action.exceptionToPropagate;
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

}
