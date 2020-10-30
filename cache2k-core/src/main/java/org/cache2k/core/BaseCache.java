package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.CacheException;
import org.cache2k.CacheOperationCompletionListener;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.common.BaseCacheControl;
import org.cache2k.core.operation.Operations;
import org.cache2k.management.CacheOperation;
import org.cache2k.management.CacheInfo;
import org.cache2k.management.CacheControl;
import org.cache2k.processor.EntryProcessingException;
import org.cache2k.processor.EntryProcessor;
import org.cache2k.processor.EntryProcessingResult;
import org.cache2k.core.operation.Semantic;
import org.cache2k.core.storageApi.StorageAdapter;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;

/**
 * Some default implementations for a cache.
 *
 * @author Jens Wilke
 */
public abstract class BaseCache<K, V> implements InternalCache<K, V> {

  @Override
  public CompletableFuture<Void> reloadAll(Iterable<? extends K> keys) {
    CompletionWrapper w = new CompletionWrapper();
    reloadAll(keys, w);
    return w.future;
  }

  @Override
  public CompletableFuture<Void> loadAll(Iterable<? extends K> keys) {
    CompletionWrapper w = new CompletionWrapper();
    loadAll(keys, w);
    return w.future;
  }

  static class CompletionWrapper implements CacheOperationCompletionListener {
    final CompletableFuture<Void> future = new CompletableFuture<>();
    @Override
    public void onCompleted() {
      future.complete(null);
    }

    @Override
    public void onException(Throwable exception) {
      future.completeExceptionally(exception);
    }
  }

  public abstract Executor getExecutor();

  protected abstract <R> EntryAction<K, V, R> createFireAndForgetAction(
    Entry<K, V> e, Semantic<K, V, R> op);

  /**
   *
   * @see HeapCache#getCompleteName() similar
   */
  public static String nameQualifier(Cache<?, ?> cache) {
    StringBuilder sb = new StringBuilder();
    sb.append('\'').append(cache.getName()).append('\'');
    if (!cache.getCacheManager().isDefaultManager()) {
      sb.append(", manager='").append(cache.getCacheManager().getName()).append('\'');
    }
    return sb.toString();
  }

  protected abstract Iterator<CacheEntry<K, V>> iterator();

  /**
   * Key iteration on top of normal iterator.
   */
  @Override
  public Iterable<K> keys() {
    return new Iterable<K>() {
      @Override
      public Iterator<K> iterator() {
        final Iterator<CacheEntry<K, V>> it = BaseCache.this.iterator();
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
        return BaseCache.this.iterator();
      }
    };
  }

  @Override
  public void removeAll() {
    removeAll(keys());
  }

  @Override
  public void removeAll(Iterable<? extends K> keys) {
    for (K k : keys) {
      remove(k);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <X> X requestInterface(Class<X> type) {
    if (type.equals(ConcurrentMap.class) ||
      type.equals(Map.class)) {
      return (X) asMap();
    }
    if (type.isAssignableFrom(CacheOperation.class) ||
      type.isAssignableFrom(CacheInfo.class) ||
      type.isAssignableFrom(CacheControl.class)) {
      return (X) new BaseCacheControl(this);
    }
    if (type.isAssignableFrom(this.getClass())) {
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
  public <R> Map<K, EntryProcessingResult<R>> invokeAll(Iterable<? extends K> keys,
                                                        EntryProcessor<K, V, R> entryProcessor) {
    Map<K, EntryProcessingResult<R>> m = new HashMap<K, EntryProcessingResult<R>>();
    for (K k : keys) {
      try {
        final R result = invoke(k, entryProcessor);
        if (result == null) {
          continue;
        }
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            return result;
          }

          @Override
          public Throwable getException() {
            return null;
          }
        });
      } catch (EntryProcessingException t) {
        final Throwable cause = t.getCause();
        m.put(k, new EntryProcessingResult<R>() {
          @Override
          public R getResult() {
            throw new EntryProcessingException(cause);
          }

          @Override
          public Throwable getException() {
            return cause;
          }
        });
      }
    }
    return m;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void expireAt(K key, long millis) {
    execute(key, Operations.SINGLETON.expire(key, millis));
  }

  protected <R> R execute(K key, Entry<K, V> e, Semantic<K, V, R> op) {
    EntryAction<K, V, R> action = createEntryAction(key, e, op);
    return execute(action);
  }

  protected abstract <R> EntryAction<K, V, R> createEntryAction(K key, Entry<K, V> e,
                                                                Semantic<K, V, R> op);

  protected <R> R execute(EntryAction<K, V, R> action) {
    action.start();
    return finishExecution(action);
  }

  /**
   * Not used for async, async will always report the exception via the callback.
   */
  protected <R> R finishExecution(EntryAction<K, V, R> action) {
    RuntimeException t = action.exceptionToPropagate;
    if (t != null) {
      t.fillInStackTrace();
      throw t;
    }
    return action.result;
  }

  protected <R> R execute(K key, Semantic<K, V, R> op) {
    return execute(key, null, op);
  }


  @Override
  public void closeCustomization(Object customization, String customizationName) {
    if (customization instanceof Closeable) {
      try {
        ((Closeable) customization).close();
      } catch (Exception e) {
        String message =
          customizationName + ".close() exception (" + nameQualifier(this) + ")";
        throw new CacheException(message, e);
      }
    }
  }

  /**
   * Return status information. The status collection is time consuming, so this
   * is an expensive operation.
   */
  @Override
  public String toString() {
    try {
      InternalCacheInfo fo = getLatestInfo();
      return fo.toString();
    } catch (CacheClosedException ex) {
      return "Cache(name=" + BaseCache.nameQualifier(this) + ", closed=true)";
    }
  }

}
