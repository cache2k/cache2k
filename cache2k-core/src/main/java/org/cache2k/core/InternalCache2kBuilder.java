package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CustomizationException;
import org.cache2k.config.CacheType;
import org.cache2k.config.CustomizationSupplier;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.eviction.EvictionFactory;
import org.cache2k.core.timing.Timing;
import org.cache2k.core.util.DefaultClock;
import org.cache2k.io.BulkCacheLoader;
import org.cache2k.operation.TimeReference;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.event.CacheCreatedListener;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.Cache;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.CacheManager;
import org.cache2k.core.event.AsyncDispatcher;
import org.cache2k.core.event.AsyncEvent;
import org.cache2k.event.CacheLifecycleListener;
import org.cache2k.io.AdvancedCacheLoader;
import org.cache2k.io.CacheLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Method object to construct a cache2k cache.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class InternalCache2kBuilder<K, V> implements InternalCacheBuildContext<K, V> {

  private static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);

  private final CacheManagerImpl manager;
  private final Cache2kConfig<K, V> config;
  private TimeReference clock;
  private Executor executor;

  public InternalCache2kBuilder(Cache2kConfig<K, V> config,
                                CacheManager manager) {
    this.config = config;
    this.manager = (CacheManagerImpl) (manager == null ? CacheManager.getInstance() : manager);
  }

  private static boolean isBuilderClass(String className) {
    return Cache2kBuilder.class.getName().equals(className);
  }

  private static String deriveNameFromStackTrace() {
    boolean builderSeen = false;
    Exception ex = new Exception();
    for (StackTraceElement e : ex.getStackTrace()) {
      if (builderSeen && !isBuilderClass(e.getClassName())) {
        String methodName = e.getMethodName();
        if (methodName.equals("<init>")) {
          methodName = "INIT";
        }
        if (methodName.equals("<clinit>")) {
          methodName = "CLINIT";
        }
        return
          "_" + e.getClassName() + "." + methodName + "-" +
          e.getLineNumber() + "-" + Long.toString(DERIVED_NAME_COUNTER.incrementAndGet(), 36);
      }
      builderSeen = isBuilderClass(e.getClassName());
    }
    throw new IllegalArgumentException("name missing and automatic generation failed");
  }

  @Override
  public String getName() {
    return config.getName();
  }

  @Override
  public TimeReference getTimeReference() {
    if (clock == null) {
      throw new IllegalStateException("Time reference not set yet");
    }
    return clock;
  }

  @Override
  public Executor getExecutor() {
    if (executor == null) {
      throw new IllegalStateException("Executor not set yet");
    }
    return executor;
  }

  @Override
  public Cache2kConfig<K, V> getConfig() {
    return config;
  }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  @Override
  public <T> T createCustomization(CustomizationSupplier<T> supplier) {
    if (supplier == null) { return null; }
    try {
      return supplier.supply(this);
    } catch (Exception ex) {
      throw new CustomizationException("Initialization of customization failed", ex);
    }
  }

  /**
   * Starting with 2.0 we don't send an entry with an exception to the loader.
   */
  public static class WrappedAdvancedCacheLoader<K, V>
    implements AdvancedCacheLoader<K, V>, AutoCloseable {

    HeapCache<K, V> heapCache;
    private final AdvancedCacheLoader<K, V> forward;

    public WrappedAdvancedCacheLoader(HeapCache<K, V> heapCache,
                               AdvancedCacheLoader<K, V> forward) {
      this.heapCache = heapCache;
      this.forward = forward;
    }

    @Override
    public void close() throws Exception {
      if (forward instanceof AutoCloseable) {
        ((AutoCloseable) forward).close();
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V load(K key, long startTime, CacheEntry<K, V> currentEntry) throws Exception {
      if (currentEntry == null || currentEntry.getExceptionInfo() != null) {
        return forward.load(key, startTime, null);
      }
      return forward.load(key, startTime, currentEntry);
    }
  }

  public Cache<K, V> build() {
    Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfig(manager, config);
    return buildWithoutExternalConfig();
  }

  /**
   * Build without applying external configuration. Needed for JCache.
   */
  @SuppressWarnings({"unchecked"})
  public Cache<K, V> buildWithoutExternalConfig() {
    if (config.getValueType() == null) {
      config.setValueType((CacheType<V>) CacheType.of(Object.class));
    }
    if (config.getKeyType() == null) {
      config.setKeyType((CacheType<K>) CacheType.of(Object.class));
    }
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
      config.setNameWasGenerated(true);
    }
    if (config.hasFeatures()) {
      config.getFeatures().stream().forEach(x -> x.enlist(this));
    }
    checkConfiguration();
    clock = createCustomization(config.getTimeReference(), DefaultClock.INSTANCE);
    executor = createCustomization(config.getExecutor(), buildContext -> ForkJoinPool.commonPool());
    HeapCache<K, V> bc;
    Class<?> keyType = config.getKeyType().getType();
    if (keyType == Integer.class) {
      bc = (HeapCache<K, V>)
        new IntHeapCache<V>((InternalCacheBuildContext<Integer, V>) this);
    } else {
      bc = new HeapCache<K, V>(this);
    }
    Cache<K, V> cache = bc;
    if (config.isRefreshAhead() && !(
          config.getAsyncLoader() != null ||
          config.getLoader() != null ||
          config.getAdvancedLoader() != null)) {
      throw new IllegalArgumentException("refresh ahead enabled, but no loader defined");
    }

    CacheLoader<K, V> loader = createCustomization(config.getLoader());
    boolean wiredCache =
      config.getWeigher() != null ||
      config.hasListeners() ||
      config.hasAsyncListeners() ||
      config.getWriter() != null ||
      config.getAsyncLoader() != null ||
      loader instanceof BulkCacheLoader;

    WiredCache<K, V> wc = null;
    if (wiredCache) {
      wc = new WiredCache<K, V>();
      wc.heapCache = bc;
      cache = wc;
    }
    if (config.getTraceCacheWrapper() != null) {
      cache = config.getTraceCacheWrapper().wrap(this, cache);
    }
    if (config.getCacheWrapper() != null) {
      cache = config.getCacheWrapper().wrap(this, cache);
    }
    if (wiredCache) {
      wc.userCache = cache;
    }
    String name = manager.newCache(cache, bc.getName());
    if (wiredCache) {
      wc.loader = bc.loader;
      if (loader instanceof BulkCacheLoader) {
        wc.bulkCacheLoader = (BulkCacheLoader<K, V>) loader;
      }
      wc.writer = createCustomization(config.getWriter());
      wc.asyncLoader = createCustomization(config.getAsyncLoader());
      List<CacheEntryCreatedListener<K, V>> syncCreatedListeners =
        new ArrayList<CacheEntryCreatedListener<K, V>>();
      List<CacheEntryUpdatedListener<K, V>> syncUpdatedListeners =
        new ArrayList<CacheEntryUpdatedListener<K, V>>();
      List<CacheEntryRemovedListener<K, V>> syncRemovedListeners =
        new ArrayList<CacheEntryRemovedListener<K, V>>();
      List<CacheEntryExpiredListener<K, V>> syncExpiredListeners =
        new ArrayList<CacheEntryExpiredListener<K, V>>();
      List<CacheEntryEvictedListener<K, V>> syncEvictedListeners =
        new ArrayList<CacheEntryEvictedListener<K, V>>();
      if (config.hasListeners()) {
        for (CustomizationSupplier<CacheEntryOperationListener<K, V>> f : config.getListeners()) {
          CacheEntryOperationListener<K, V> el =
            createCustomization(f);
          if (el instanceof CacheEntryCreatedListener) {
            syncCreatedListeners.add((CacheEntryCreatedListener) el);
          }
          if (el instanceof CacheEntryUpdatedListener) {
            syncUpdatedListeners.add((CacheEntryUpdatedListener) el);
          }
          if (el instanceof CacheEntryRemovedListener) {
            syncRemovedListeners.add((CacheEntryRemovedListener) el);
          }
          if (el instanceof CacheEntryExpiredListener) {
            syncExpiredListeners.add((CacheEntryExpiredListener) el);
          }
          if (el instanceof CacheEntryEvictedListener) {
            syncEvictedListeners.add((CacheEntryEvictedListener) el);
          }
        }
      }
      if (config.hasAsyncListeners()) {
        Executor asyncExecutor = bc.getExecutor();
        if (config.getAsyncListenerExecutor() != null) {
          asyncExecutor = createCustomization(config.getAsyncListenerExecutor());
        }
        AsyncDispatcher<K> asyncDispatcher = new AsyncDispatcher<K>(wc, asyncExecutor);
        List<CacheEntryCreatedListener<K, V>> cll =
          new ArrayList<CacheEntryCreatedListener<K, V>>();
        List<CacheEntryUpdatedListener<K, V>> ull =
          new ArrayList<CacheEntryUpdatedListener<K, V>>();
        List<CacheEntryRemovedListener<K, V>> rll =
          new ArrayList<CacheEntryRemovedListener<K, V>>();
        List<CacheEntryExpiredListener<K, V>> ell =
          new ArrayList<CacheEntryExpiredListener<K, V>>();
        List<CacheEntryEvictedListener<K, V>> evl =
          new ArrayList<CacheEntryEvictedListener<K, V>>();
        for (CustomizationSupplier<CacheEntryOperationListener<K, V>> f :
          config.getAsyncListeners()) {
          CacheEntryOperationListener<K, V> el =
            createCustomization(f);
          if (el instanceof CacheEntryCreatedListener) {
            cll.add((CacheEntryCreatedListener) el);
          }
          if (el instanceof CacheEntryUpdatedListener) {
            ull.add((CacheEntryUpdatedListener) el);
          }
          if (el instanceof CacheEntryRemovedListener) {
            rll.add((CacheEntryRemovedListener) el);
          }
          if (el instanceof CacheEntryExpiredListener) {
            ell.add((CacheEntryExpiredListener) el);
          }
          if (el instanceof CacheEntryEvictedListener) {
            evl.add((CacheEntryEvictedListener) el);
          }
        }
        for (CacheEntryCreatedListener l : cll) {
          syncCreatedListeners.add(new AsyncCreatedListener<K, V>(asyncDispatcher, l));
        }
        for (CacheEntryUpdatedListener l : ull) {
          syncUpdatedListeners.add(new AsyncUpdatedListener<K, V>(asyncDispatcher, l));
        }
        for (CacheEntryRemovedListener l : rll) {
          syncRemovedListeners.add(new AsyncRemovedListener<K, V>(asyncDispatcher, l));
        }
        for (CacheEntryExpiredListener l : ell) {
          syncExpiredListeners.add(new AsyncExpiredListener<K, V>(asyncDispatcher, l));
        }
        for (CacheEntryEvictedListener l : evl) {
          syncEvictedListeners.add(new AsyncEvictedListener<K, V>(asyncDispatcher, l));
        }
      }
      if (!syncCreatedListeners.isEmpty()) {
        wc.syncEntryCreatedListeners =
          syncCreatedListeners.toArray(new CacheEntryCreatedListener[0]);
      }
      if (!syncUpdatedListeners.isEmpty()) {
        wc.syncEntryUpdatedListeners =
          syncUpdatedListeners.toArray(new CacheEntryUpdatedListener[0]);
      }
      if (!syncRemovedListeners.isEmpty()) {
        wc.syncEntryRemovedListeners =
          syncRemovedListeners.toArray(new CacheEntryRemovedListener[0]);
      }
      if (!syncExpiredListeners.isEmpty()) {
        wc.syncEntryExpiredListeners =
          syncExpiredListeners.toArray(new CacheEntryExpiredListener[0]);
      }
      if (!syncEvictedListeners.isEmpty()) {
        wc.syncEntryEvictedListeners =
          syncEvictedListeners.toArray(new CacheEntryEvictedListener[0]);
      }
      bc.eviction = EVICTION_FACTORY.constructEviction(
        this, bc, wc, config, Runtime.getRuntime().availableProcessors());
      Timing rh = Timing.of(this);
      bc.setTiming(rh);
      wc.init();
    } else {
      Timing rh = Timing.of(this);
      bc.setTiming(rh);
      bc.eviction = EVICTION_FACTORY.constructEviction(
        this, bc, HeapCacheListener.NO_OPERATION, config,
        Runtime.getRuntime().availableProcessors());
      bc.init();
    }
    if (config.hasLifecycleListeners()) {
      List<CacheClosedListener> closedListeners = new ArrayList<CacheClosedListener>();
      for (CustomizationSupplier<? extends CacheLifecycleListener> sup :
        config.getLifecycleListeners()) {
        CacheLifecycleListener l = createCustomization(sup);
        if (l instanceof CacheClosedListener) {
          closedListeners.add((CacheClosedListener) l);
        }
        if (l instanceof CacheCreatedListener) {
          ((CacheCreatedListener) l).onCacheCreated(cache, this);
        }
      }
      bc.cacheClosedListeners = closedListeners;
    }
    manager.sendCreatedEvent(cache, this);
    return cache;
  }

  static final EvictionFactory EVICTION_FACTORY = new EvictionFactory();

  private void checkConfiguration() {
    if (config.getExpireAfterWrite() == Cache2kConfig.EXPIRY_NOT_ETERNAL &&
        config.getExpiryPolicy() == null) {
      throw new IllegalArgumentException("not eternal is set, but expire value is missing");
    }
  }

  static class AsyncCreatedListener<K, V> implements CacheEntryCreatedListener<K, V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryCreatedListener<K, V> listener;

    AsyncCreatedListener(AsyncDispatcher<K> dispatcher,
                         CacheEntryCreatedListener<K, V> listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }

    @Override
    public void onEntryCreated(Cache<K, V> c, CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() throws Exception {
          listener.onEntryCreated(c, e);
        }
      });
    }

  }

  private static class AsyncUpdatedListener<K, V> implements CacheEntryUpdatedListener<K, V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryUpdatedListener<K, V> listener;

    AsyncUpdatedListener(AsyncDispatcher<K> dispatcher,
                                CacheEntryUpdatedListener<K, V> listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }

    @Override
    public void onEntryUpdated(Cache<K, V> cache, CacheEntry<K, V> currentEntry,
                               CacheEntry<K, V> newEntry) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return currentEntry.getKey();
        }

        @Override
        public void execute() throws Exception {
          listener.onEntryUpdated(cache, currentEntry, newEntry);
        }
      });
    }

  }

  private static class AsyncRemovedListener<K, V> implements CacheEntryRemovedListener<K, V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryRemovedListener<K, V> listener;

    AsyncRemovedListener(AsyncDispatcher<K> dispatcher,
                                CacheEntryRemovedListener<K, V> listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }

    @Override
    public void onEntryRemoved(Cache<K, V> c, CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() throws Exception {
          listener.onEntryRemoved(c, e);
        }
      });
    }
  }

  private static class AsyncExpiredListener<K, V> implements CacheEntryExpiredListener<K, V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryExpiredListener<K, V> listener;

    AsyncExpiredListener(AsyncDispatcher<K> dispatcher,
                                CacheEntryExpiredListener<K, V> listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }

    @Override
    public void onEntryExpired(Cache<K, V> c, CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() throws Exception {
          listener.onEntryExpired(c, e);
        }
      });
    }
  }

  private static class AsyncEvictedListener<K, V> implements CacheEntryEvictedListener<K, V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryEvictedListener<K, V> listener;

    AsyncEvictedListener(AsyncDispatcher<K> dispatcher,
                         CacheEntryEvictedListener<K, V> listener) {
      this.dispatcher = dispatcher;
      this.listener = listener;
    }

    @Override
    public void onEntryEvicted(Cache<K, V> c, CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() throws Exception {
          listener.onEntryEvicted(c, e);
        }
      });
    }
  }

}
