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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CustomizationException;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.core.eviction.EvictionFactory;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.timing.Timing;
import org.cache2k.core.util.DefaultClock;
import org.cache2k.core.util.InternalClock;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryEvictedListener;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.Cache;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.CacheManager;
import org.cache2k.core.event.AsyncDispatcher;
import org.cache2k.core.event.AsyncEvent;
import org.cache2k.integration.AdvancedCacheLoader;
import org.cache2k.integration.AsyncCacheLoader;
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.FunctionalCacheLoader;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Method object to construct a cache2k cache.
 *
 * @author Jens Wilke
 */
@SuppressWarnings("rawtypes")
public class InternalCache2kBuilder<K, V> implements CacheBuildContext<K, V> {

  private static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);

  private final CacheManagerImpl manager;
  private final Cache2kConfiguration<K, V> config;
  private InternalClock clock;

  public InternalCache2kBuilder(Cache2kConfiguration<K, V> config,
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
  public InternalClock getClock() {
    return clock;
  }

  @Override
  public Cache2kConfiguration<K, V> getConfiguration() {
    return config;
  }

  @Override
  public CacheManager getCacheManager() {
    return manager;
  }

  @Override
  public <T> T createCustomization(CustomizationSupplier<T> supplier, T fallback) {
    if (supplier == null) { return fallback; }
    try {
      return supplier.supply(getCacheManager());
    } catch (Exception ex) {
      throw new CustomizationException("Initialization of customization failed", ex);
    }
  }

  public <T> T createCustomization(CustomizationSupplier<T> supplier) {
    return createCustomization(supplier, null);
  }

  /**
   * The generic wiring code is not working on android.
   * Explicitly call the wiring methods.
   */
  @SuppressWarnings("unchecked")
  private void configureViaSettersDirect(HeapCache<K, V> c) {
    if (config.getLoader() != null) {
      Object obj =  createCustomization(config.getLoader());
      if (obj instanceof CacheLoader) {
        final CacheLoader<K, V> loader = (CacheLoader) obj;
        c.setAdvancedLoader(new AdvancedCacheLoader<K, V>() {
          @Override
          public V load(K key, long startTime, CacheEntry<K, V> currentEntry)
            throws Exception {
            return loader.load(key);
          }
        });
      } else {
        final FunctionalCacheLoader<K, V> loader = (FunctionalCacheLoader) obj;
        c.setAdvancedLoader(new AdvancedCacheLoader<K, V>() {
          @Override
          public V load(K key, long startTime, CacheEntry<K, V> currentEntry)
            throws Exception {
            return loader.load(key);
          }
        });
      }
    }
    if (config.getAdvancedLoader() != null) {
      AdvancedCacheLoader<K, V> loader = createCustomization(config.getAdvancedLoader());
      AdvancedCacheLoader<K, V> wrappedLoader = new WrappedAdvancedCacheLoader<K, V>(c, loader);
      c.setAdvancedLoader(wrappedLoader);
    }
    if (config.getExceptionPropagator() != null) {
      c.setExceptionPropagator(createCustomization(config.getExceptionPropagator()));
    }
    c.setCacheConfig(this);
  }

  private static class WrappedAdvancedCacheLoader<K, V> extends AdvancedCacheLoader<K, V>
    implements Closeable {

    HeapCache<K, V> heapCache;
    private final AdvancedCacheLoader<K, V> forward;

    WrappedAdvancedCacheLoader(HeapCache<K, V> heapCache,
                               AdvancedCacheLoader<K, V> forward) {
      this.heapCache = heapCache;
      this.forward = forward;
    }

    @Override
    public void close() throws IOException {
      if (forward instanceof Closeable) {
        ((Closeable) forward).close();
      }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V load(K key, long startTime, CacheEntry<K, V> currentEntry) throws Exception {
      if (currentEntry == null) {
        return forward.load(key, startTime, null);
      }
      return forward.load(key, startTime,
        heapCache.returnCacheEntry((ExaminationEntry<K, V>) currentEntry));
    }
  }

  public Cache<K, V> build() {
    Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(manager, config);
    return buildAsIs();
  }

  /**
   * Build without applying external configuration. Needed for JCache.
   */
  @SuppressWarnings({"unchecked"})
  public Cache<K, V> buildAsIs() {
    if (config.getValueType() == null) {
      config.setValueType((Class<V>) Object.class);
    }
    if (config.getKeyType() == null) {
      config.setKeyType((Class<K>) Object.class);
    }
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
    }
    checkConfiguration();
    InternalCache<K, V> cache;
    Class<?> keyType = config.getKeyType().getType();
    if (keyType == Integer.class) {
      cache = (InternalCache<K, V>) new IntHeapCache<V>();
    } else if (keyType == Long.class) {
      cache = (InternalCache<K, V>) new LongHeapCache<V>();
    } else {
      cache = new HeapCache<K, V>();
    }
    clock = (InternalClock) createCustomization(config.getTimeReference());
    if (clock == null) {
      clock = DefaultClock.INSTANCE;
    }
    HeapCache bc = (HeapCache) cache;
    bc.setCacheManager(manager);
    configureViaSettersDirect(bc);
    bc.setClock(clock);

    if (config.isRefreshAhead() && !(
          config.getAsyncLoader() != null ||
          config.getLoader() != null ||
          config.getAdvancedLoader() != null)) {
      throw new IllegalArgumentException("refresh ahead enabled, but no loader defined");
    }

    boolean wrap =
      config.getWeigher() != null ||
      config.hasListeners() ||
      config.hasAsyncListeners() ||
      config.getWriter() != null ||
      config.getAsyncLoader() != null;


    WiredCache<K, V> wc = null;
    if (wrap) {
      if (keyType == Integer.class) {
        wc = (WiredCache<K, V>) new IntWiredCache<V>();
      } else if (keyType == Long.class) {
        wc = (WiredCache<K, V>) new LongWiredCache<V>();
      } else {
        wc = new WiredCache<K, V>();
      }
      wc.heapCache = bc;
      cache = wc;
    }

    String name = manager.newCache(cache, bc.getName());
    bc.setName(name);
    if (wrap) {
      wc.loader = bc.loader;
      wc.writer = (CacheWriter<K, V>) createCustomization(config.getWriter());
      wc.asyncLoader = (AsyncCacheLoader<K, V>) createCustomization(config.getAsyncLoader());
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
            (CacheEntryOperationListener<K, V>) createCustomization(f);
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
            (CacheEntryOperationListener<K, V>) createCustomization(f);
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
        this, bc, HeapCacheListener.NO_OPERATION, config, Runtime.getRuntime().availableProcessors());
      bc.init();
    }
    manager.sendCreatedEvent(cache, config);
    return cache;
  }

  static final EvictionFactory EVICTION_FACTORY = new EvictionFactory();

  private void checkConfiguration() {
    if (config.getExpireAfterWrite() == Cache2kConfiguration.EXPIRY_NOT_ETERNAL &&
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
    public void onEntryCreated(final Cache<K, V> c, final CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() {
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
    public void onEntryUpdated(final Cache<K, V> cache, final CacheEntry<K, V> currentEntry,
                               final CacheEntry<K, V> entryWithNewData) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return currentEntry.getKey();
        }

        @Override
        public void execute() {
          listener.onEntryUpdated(cache, currentEntry, entryWithNewData);
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
    public void onEntryRemoved(final Cache<K, V> c, final CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() {
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
    public void onEntryExpired(final Cache<K, V> c, final CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() {
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
    public void onEntryEvicted(final Cache<K, V> c, final CacheEntry<K, V> e) {
      dispatcher.queue(new AsyncEvent<K>() {
        @Override
        public K getKey() {
          return e.getKey();
        }

        @Override
        public void execute() {
          listener.onEntryEvicted(c, e);
        }
      });
    }
  }

}
