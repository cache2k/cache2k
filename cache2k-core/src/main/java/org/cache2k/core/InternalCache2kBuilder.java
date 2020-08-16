package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
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
import org.cache2k.Weigher;
import org.cache2k.configuration.CustomizationSupplier;
import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.util.ClockDefaultImpl;
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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Method object to construct a cache2k cache.
 *
 * @author Jens Wilke
 */
public class InternalCache2kBuilder<K, V> {

  private static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);
  private static final ThreadPoolExecutor DEFAULT_ASYNC_LISTENER_EXECUTOR =
    new ThreadPoolExecutor(
      Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
      21, TimeUnit.SECONDS,
      new LinkedBlockingDeque<Runnable>(),
      HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory("cache2k-listener"),
      new ThreadPoolExecutor.AbortPolicy());

  private CacheManagerImpl manager;
  private Cache2kConfiguration<K, V> config;

  public InternalCache2kBuilder(final Cache2kConfiguration<K, V> config,
                                final CacheManager manager) {
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

  /**
   * The generic wiring code is not working on android.
   * Explicitly call the wiring methods.
   */
  @SuppressWarnings("unchecked")
  private void configureViaSettersDirect(HeapCache<K,V> c) {
    if (config.getLoader() != null) {
      Object obj =  c.createCustomization(config.getLoader());
      if (obj instanceof CacheLoader) {
        final CacheLoader<K,V> loader = (CacheLoader) obj;
        c.setAdvancedLoader(new AdvancedCacheLoader<K, V>() {
          @Override
          public V load(final K key, final long startTime, final CacheEntry<K, V> currentEntry) throws Exception {
            return loader.load(key);
          }
        });
      } else {
        final FunctionalCacheLoader<K,V> loader = (FunctionalCacheLoader) obj;
        c.setAdvancedLoader(new AdvancedCacheLoader<K, V>() {
          @Override
          public V load(final K key, final long startTime, final CacheEntry<K, V> currentEntry) throws Exception {
            return loader.load(key);
          }
        });
      }
    }
    if (config.getAdvancedLoader() != null) {
      final AdvancedCacheLoader<K,V> loader = c.createCustomization(config.getAdvancedLoader());
      AdvancedCacheLoader<K,V> wrappedLoader = new WrappedAdvancedCacheLoader<K, V>(c, loader);
      c.setAdvancedLoader(wrappedLoader);
    }
    if (config.getExceptionPropagator() != null) {
      c.setExceptionPropagator(c.createCustomization(config.getExceptionPropagator()));
    }
    c.setCacheConfig(config);
  }

  private static class WrappedAdvancedCacheLoader<K,V> extends AdvancedCacheLoader<K,V> implements Closeable {

    HeapCache<K,V> heapCache;
    private final AdvancedCacheLoader<K,V> forward;

    public WrappedAdvancedCacheLoader(final HeapCache<K, V> heapCache,
                                      final AdvancedCacheLoader<K, V> forward) {
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
    public V load(final K key, final long startTime, final CacheEntry<K, V> currentEntry) throws Exception {
      if (currentEntry == null) {
        return forward.load(key, startTime, null);
      }
      return forward.load(key, startTime, heapCache.returnCacheEntry((ExaminationEntry<K, V>) currentEntry));
    }
  }

  @SuppressWarnings("unchecked")
  private HeapCache<K, V> constructImplementationAndFillParameters(Class<?> cls) {
    if (!HeapCache.class.isAssignableFrom(cls)) {
      throw new IllegalArgumentException("Specified impl not a cache" + cls.getName());
    }
    try {
      return (HeapCache<K, V>) cls.newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException("Not able to instantiate cache implementation", e);
    }
  }

  public Cache<K, V> build() {
    Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(manager, config);
    return buildAsIs();
  }

  /**
   * Build without applying external configuration. Needed for JCache.
   */
  @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
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
    Class<?> implClass = HeapCache.class;
    Class<?> keyType = config.getKeyType().getType();
    if (keyType == Integer.class) {
      implClass = IntHeapCache.class;
    } else if (keyType == Long.class) {
      implClass = LongHeapCache.class;
    }
    InternalCache<K, V> cache = constructImplementationAndFillParameters(implClass);
    InternalClock timeReference = (InternalClock) cache.createCustomization(config.getTimeReference());
    if (timeReference == null) {
      timeReference = ClockDefaultImpl.INSTANCE;
    }
    HeapCache bc = (HeapCache) cache;
    bc.setCacheManager(manager);
    if (config.hasCacheClosedListeners()) {
      bc.setCacheClosedListeners(config.getCacheClosedListeners());
    }
    configureViaSettersDirect(bc);
    bc.setClock(timeReference);

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
      wc.writer = (CacheWriter<K, V>) bc.createCustomization(config.getWriter());
      wc.asyncLoader = (AsyncCacheLoader<K, V>) bc.createCustomization(config.getAsyncLoader());
      List<CacheEntryCreatedListener<K, V>> syncCreatedListeners = new ArrayList<CacheEntryCreatedListener<K, V>>();
      List<CacheEntryUpdatedListener<K, V>> syncUpdatedListeners = new ArrayList<CacheEntryUpdatedListener<K, V>>();
      List<CacheEntryRemovedListener<K, V>> syncRemovedListeners = new ArrayList<CacheEntryRemovedListener<K, V>>();
      List<CacheEntryExpiredListener<K, V>> syncExpiredListeners = new ArrayList<CacheEntryExpiredListener<K, V>>();
      List<CacheEntryEvictedListener<K, V>> syncEvictedListeners = new ArrayList<CacheEntryEvictedListener<K,V>>();
      if (config.hasListeners()) {
        for (CustomizationSupplier<CacheEntryOperationListener<K, V>> f : config.getListeners()) {
          CacheEntryOperationListener<K, V> el =
            (CacheEntryOperationListener<K, V>) bc.createCustomization(f);
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
        Executor executor = DEFAULT_ASYNC_LISTENER_EXECUTOR;
        if (config.getAsyncListenerExecutor() != null) {
          executor = cache.createCustomization(config.getAsyncListenerExecutor());
        }
        AsyncDispatcher<K> asyncDispatcher = new AsyncDispatcher<K>(wc, executor);
        List<CacheEntryCreatedListener<K, V>> cll = new ArrayList<CacheEntryCreatedListener<K, V>>();
        List<CacheEntryUpdatedListener<K, V>> ull = new ArrayList<CacheEntryUpdatedListener<K, V>>();
        List<CacheEntryRemovedListener<K, V>> rll = new ArrayList<CacheEntryRemovedListener<K, V>>();
        List<CacheEntryExpiredListener<K, V>> ell = new ArrayList<CacheEntryExpiredListener<K, V>>();
        List<CacheEntryEvictedListener<K, V>> evl = new ArrayList<CacheEntryEvictedListener<K, V>>();
        for (CustomizationSupplier<CacheEntryOperationListener<K, V>> f : config.getAsyncListeners()) {
          CacheEntryOperationListener<K, V> el = (CacheEntryOperationListener<K, V>) bc.createCustomization(f);
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
        wc.syncEntryCreatedListeners = syncCreatedListeners.toArray(new CacheEntryCreatedListener[0]);
      }
      if (!syncUpdatedListeners.isEmpty()) {
        wc.syncEntryUpdatedListeners = syncUpdatedListeners.toArray(new CacheEntryUpdatedListener[0]);
      }
      if (!syncRemovedListeners.isEmpty()) {
        wc.syncEntryRemovedListeners = syncRemovedListeners.toArray(new CacheEntryRemovedListener[0]);
      }
      if (!syncExpiredListeners.isEmpty()) {
        wc.syncEntryExpiredListeners = syncExpiredListeners.toArray(new CacheEntryExpiredListener[0]);
      }
      if (!syncEvictedListeners.isEmpty()) {
        wc.syncEntryEvictedListeners = syncEvictedListeners.toArray(new CacheEntryEvictedListener[0]);
      }
      bc.eviction = constructEviction(bc, wc, config);
      TimingHandler rh = TimingHandler.of(timeReference, config);
      bc.setTiming(rh);
      wc.init();
    } else {
      TimingHandler rh = TimingHandler.of(timeReference, config);
      bc.setTiming(rh);
       bc.eviction = constructEviction(bc, HeapCacheListener.NO_OPERATION, config);
      bc.init();
    }
    manager.sendCreatedEvent(cache, config);
    return cache;
  }

  /**
   * Construct segmented or queued eviction. For the moment hard coded.
   * If capacity is at least 1000 we use 2 segments if 2 or more CPUs are available.
   * Segmenting the eviction only improves for lots of concurrent inserts or evictions,
   * there is no effect on read performance.
   */
  private Eviction constructEviction(HeapCache hc, HeapCacheListener l, Cache2kConfiguration config) {
    final boolean strictEviction = config.isStrictEviction();
    final int availableProcessors = Runtime.getRuntime().availableProcessors();
    final boolean boostConcurrency = config.isBoostConcurrency();
    final long maximumWeight = config.getMaximumWeight();
    long entryCapacity = config.getEntryCapacity();
    if (entryCapacity < 0 && maximumWeight < 0) {
      entryCapacity = 2000;
    }
    final int segmentCountOverride = HeapCache.TUNABLE.segmentCountOverride;
    int segmentCount =
      determineSegmentCount(
        strictEviction, availableProcessors,
        boostConcurrency, entryCapacity, segmentCountOverride);
    Eviction[] segments = new Eviction[segmentCount];
    long maxSize = determineMaxSize(entryCapacity, segmentCount);
    long maxWeight = determineMaxWeight(maximumWeight, segmentCount);
    final Weigher weigher = (Weigher) hc.createCustomization(config.getWeigher());
    for (int i = 0; i < segments.length; i++) {
      Eviction ev = new ClockProPlusEviction(hc, l, maxSize, weigher, maxWeight, strictEviction);
      segments[i] = ev;
    }
    if (segmentCount == 1) {
      return segments[0];
    }
    return new SegmentedEviction(segments);
  }

  static long determineMaxSize(final long entryCapacity, final int segmentCount) {
    if (entryCapacity < 0) {
      return -1;
    }
    long maxSize = entryCapacity / segmentCount;
    if (entryCapacity == Long.MAX_VALUE) {
      maxSize = Long.MAX_VALUE;
    } else if (entryCapacity % segmentCount > 0) {
      maxSize++;
    }
    return maxSize;
  }

  static long determineMaxWeight(final long maximumWeight, final int segmentCount) {
    if (maximumWeight < 0) {
      return -1;
    }
    long maxWeight = maximumWeight / segmentCount;
    if (maximumWeight == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    } else if (maximumWeight % segmentCount > 0) {
      maxWeight++;
    }
    return maxWeight;
  }

  static int determineSegmentCount(final boolean strictEviction, final int availableProcessors,
                                   final boolean boostConcurrency, final long entryCapacity,
                                   final int segmentCountOverride) {
    int segmentCount = 1;
    if (availableProcessors > 1) {
      segmentCount = 2;
      if (boostConcurrency) {
        segmentCount = 2 << (31 - Integer.numberOfLeadingZeros(availableProcessors));
      }
    }
    if (segmentCountOverride > 0) {
      segmentCount = 1 << (32 - Integer.numberOfLeadingZeros(segmentCountOverride - 1));
    } else {
      int maxSegments = availableProcessors * 2;
      segmentCount = Math.min(segmentCount, maxSegments);
    }
    if (entryCapacity >= 0 && entryCapacity < 1000) {
      segmentCount = 1;
    }
    if (strictEviction) {
      segmentCount = 1;
    }
    return segmentCount;
  }

  private void checkConfiguration() {
    if (config.getExpireAfterWrite() == Cache2kConfiguration.EXPIRY_NOT_ETERNAL &&
        config.getExpiryPolicy() == null) {
      throw new IllegalArgumentException("not eternal is set, but expire value is missing");
    }
  }

  static class AsyncCreatedListener<K,V> implements CacheEntryCreatedListener<K,V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryCreatedListener<K,V> listener;

    public AsyncCreatedListener(final AsyncDispatcher<K> dispatcher,
                                final CacheEntryCreatedListener<K, V> listener) {
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

  private static class AsyncUpdatedListener<K,V> implements CacheEntryUpdatedListener<K,V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryUpdatedListener<K,V> listener;

    public AsyncUpdatedListener(final AsyncDispatcher<K> dispatcher,
                                final CacheEntryUpdatedListener<K, V> listener) {
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

  private static class AsyncRemovedListener<K,V> implements CacheEntryRemovedListener<K,V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryRemovedListener<K,V> listener;

    public AsyncRemovedListener(final AsyncDispatcher<K> dispatcher,
                                final CacheEntryRemovedListener<K, V> listener) {
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

  private static class AsyncExpiredListener<K,V> implements CacheEntryExpiredListener<K,V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryExpiredListener<K,V> listener;

    public AsyncExpiredListener(final AsyncDispatcher<K> dispatcher,
                                final CacheEntryExpiredListener<K, V> listener) {
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

  private static class AsyncEvictedListener<K,V> implements CacheEntryEvictedListener<K,V> {
    AsyncDispatcher<K> dispatcher;
    CacheEntryEvictedListener<K,V> listener;

    public AsyncEvictedListener(final AsyncDispatcher<K> dispatcher,
                                final CacheEntryEvictedListener<K, V> listener) {
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
