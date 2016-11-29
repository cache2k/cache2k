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

import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.CacheMisconfigurationException;
import org.cache2k.configuration.CustomizationFactory;
import org.cache2k.event.CacheEntryCreatedListener;
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
import org.cache2k.integration.CacheLoader;
import org.cache2k.integration.CacheWriter;
import org.cache2k.integration.ExceptionPropagator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Method object to construct a cache2k cache.
 *
 * @author Jens Wilke; created: 2013-12-06
 */
public class InternalCache2kBuilder<K, V> {

  private static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);
  private static final ThreadPoolExecutor ASYNC_EXECUTOR =
    new ThreadPoolExecutor(
      Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
      21, TimeUnit.SECONDS,
      new LinkedBlockingDeque<Runnable>(),
      HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory("cache2k-async"),
      new ThreadPoolExecutor.AbortPolicy());

  private CacheManagerImpl manager;
  private Cache2kConfiguration<K, V> config;

  public InternalCache2kBuilder(final Cache2kConfiguration<K, V> _config, final CacheManager _manager) {
    config = _config;
    manager = (CacheManagerImpl) (_manager == null ? CacheManager.getInstance() : _manager);
  }

  private static boolean isBuilderClass(String _className) {
    return CacheBuilder.class.getName().equals(_className) ||
        Cache2kBuilder.class.getName().equals(_className);
  }

  private static String deriveNameFromStackTrace() {
    boolean _builderSeen = false;
    Exception ex = new Exception();
    for (StackTraceElement e : ex.getStackTrace()) {
      if (_builderSeen && !isBuilderClass(e.getClassName())) {
        String _methodName = e.getMethodName();
        if (_methodName.equals("<init>")) {
          _methodName = "INIT";
        }
        if (_methodName.equals("<clinit>")) {
          _methodName = "CLINIT";
        }
        return
          "_" + e.getClassName() + "." + _methodName + "-" +
          e.getLineNumber() + "-" + Long.toString(DERIVED_NAME_COUNTER.incrementAndGet(), 36);
      }
      _builderSeen = isBuilderClass(e.getClassName());
    }
    throw new IllegalArgumentException("name missing and automatic generation failed");
  }

  /**
   * The generic wiring code is not working on android.
   * Explicitly call the wiring methods.
   */
  @SuppressWarnings("unchecked")
  private void configureViaSettersDirect(HeapCache c) {
    if (config.getLoader() != null) {
      c.setLoader((CacheLoader<K, V>) c.createCustomization(config.getLoader()));
    }
    if (config.getAdvancedLoader() != null) {
      c.setAdvancedLoader((AdvancedCacheLoader<K, V>) c.createCustomization(config.getAdvancedLoader()));
    }
    if (config.getExceptionPropagator() != null) {
      c.setExceptionPropagator((ExceptionPropagator<K>) c.createCustomization(config.getExceptionPropagator()));
    }
    if (config != null) {
      c.setCacheConfig(config);
    }
  }

  private HeapCache<K, V> constructImplementationAndFillParameters(Class<?> cls) {
    if (!HeapCache.class.isAssignableFrom(cls)) {
      throw new IllegalArgumentException("Specified impl not a cache" + cls.getName());
    }
    try {
      return (HeapCache<K, V>) cls.newInstance();
    } catch (Exception e) {
      throw new CacheMisconfigurationException("Not able to instantiate cache implementation", e);
    }
  }

  @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
  public Cache<K, V> build() {
    if (Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER != null) {
      Cache2kCoreProviderImpl.CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(manager, config);
    }
    if (config.getValueType() == null) {
      config.setValueType(Object.class);
    }
    if (config.getKeyType() == null) {
      config.setKeyType(Object.class);
    }
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
    }
    checkConfiguration();
    Class<?> _implClass = HeapCache.TUNABLE.defaultImplementation;
    InternalCache<K, V> _cache = constructImplementationAndFillParameters(_implClass);

    HeapCache bc = (HeapCache) _cache;
    bc.setCacheManager(manager);
    configureViaSettersDirect(bc);

    boolean _wrap = false;

    if (config.hasListeners()) { _wrap = true; }
    if (config.hasAsyncListeners()) { _wrap = true; }
    if (config.getWriter() != null) { _wrap = true; }

    WiredCache<K, V> wc = null;
    if (_wrap) {
      wc = new WiredCache<K, V>();
      wc.heapCache = bc;
      _cache = wc;
    }

    String _name = manager.newCache(_cache, bc.getName());
    bc.setName(_name);
    if (_wrap) {
      wc.loader = bc.loader;
      if (config.getWriter() != null) {
        wc.writer = (CacheWriter<K, V>) bc.createCustomization(config.getWriter());
      }
      List<CacheEntryCreatedListener<K, V>> _syncCreatedListeners = new ArrayList<CacheEntryCreatedListener<K, V>>();
      List<CacheEntryUpdatedListener<K, V>> _syncUpdatedListeners = new ArrayList<CacheEntryUpdatedListener<K, V>>();
      List<CacheEntryRemovedListener<K, V>> _syncRemovedListeners = new ArrayList<CacheEntryRemovedListener<K, V>>();
      List<CacheEntryExpiredListener<K, V>> _syncExpiredListeners = new ArrayList<CacheEntryExpiredListener<K, V>>();
      List<CacheEntryExpiredListener<K, V>> _expiredListeners = new ArrayList<CacheEntryExpiredListener<K, V>>();
      if (config.hasListeners()) {
        for (CustomizationFactory<CacheEntryOperationListener<K, V>> f : config.getListeners()) {
          CacheEntryOperationListener<K, V> el = ( CacheEntryOperationListener<K, V>) bc.createCustomization(f);
          if (el instanceof CacheEntryCreatedListener) {
            _syncCreatedListeners.add((CacheEntryCreatedListener) el);
          }
          if (el instanceof CacheEntryUpdatedListener) {
            _syncUpdatedListeners.add((CacheEntryUpdatedListener) el);
          }
          if (el instanceof CacheEntryRemovedListener) {
            _syncRemovedListeners.add((CacheEntryRemovedListener) el);
          }
          if (el instanceof CacheEntryExpiredListener) {
            _expiredListeners.add((CacheEntryExpiredListener) el);
          }
        }
      }
      if (config.hasAsyncListeners() || !_expiredListeners.isEmpty()) {
        AsyncDispatcher<K> _asyncDispatcher = new AsyncDispatcher<K>(wc, ASYNC_EXECUTOR);
        List<CacheEntryCreatedListener<K, V>> cll = new ArrayList<CacheEntryCreatedListener<K, V>>();
        List<CacheEntryUpdatedListener<K, V>> ull = new ArrayList<CacheEntryUpdatedListener<K, V>>();
        List<CacheEntryRemovedListener<K, V>> rll = new ArrayList<CacheEntryRemovedListener<K, V>>();
        List<CacheEntryExpiredListener<K, V>> ell = new ArrayList<CacheEntryExpiredListener<K, V>>();
        for (CustomizationFactory<CacheEntryOperationListener<K, V>> f : config.getAsyncListeners()) {
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
        }
        for (CacheEntryCreatedListener l : cll) {
          _syncCreatedListeners.add(new AsyncCreatedListener<K, V>(_asyncDispatcher, l));
        }
        for (CacheEntryUpdatedListener l : ull) {
          _syncUpdatedListeners.add(new AsyncUpdatedListener<K, V>(_asyncDispatcher, l));
        }
        for (CacheEntryRemovedListener l : rll) {
          _syncRemovedListeners.add(new AsyncRemovedListener<K, V>(_asyncDispatcher, l));
        }
        for (CacheEntryExpiredListener l : ell) {
          _syncExpiredListeners.add(new AsyncExpiredListener<K, V>(_asyncDispatcher, l));
        }
        for (CacheEntryExpiredListener l : _expiredListeners) {
          _syncExpiredListeners.add(new AsyncExpiredListener<K, V>(_asyncDispatcher, l));
        }
      }
      if (!_syncCreatedListeners.isEmpty()) {
        wc.syncEntryCreatedListeners = _syncCreatedListeners.toArray(new CacheEntryCreatedListener[_syncCreatedListeners.size()]);
      }
      if (!_syncUpdatedListeners.isEmpty()) {
        wc.syncEntryUpdatedListeners = _syncUpdatedListeners.toArray(new CacheEntryUpdatedListener[_syncUpdatedListeners.size()]);
      }
      if (!_syncRemovedListeners.isEmpty()) {
        wc.syncEntryRemovedListeners = _syncRemovedListeners.toArray(new CacheEntryRemovedListener[_syncRemovedListeners.size()]);
      }
      if (!_syncExpiredListeners.isEmpty()) {
        wc.syncEntryExpiredListeners = _syncExpiredListeners.toArray(new CacheEntryExpiredListener[_syncExpiredListeners.size()]);
      }
      bc.eviction = constructEviction(bc, wc, config);
      TimingHandler rh = TimingHandler.of(config);
      bc.setTiming(rh);
      wc.init();
    } else {
      TimingHandler rh = TimingHandler.of(config);
      bc.setTiming(rh);
      bc.eviction = constructEviction(bc, new HeapCacheListener.NoOperation(), config);
      bc.init();
    }
    manager.sendCreatedEvent(_cache);
    return _cache;
  }

  /**
   * Construct segmented or queued eviction. For the moment hard coded.
   * If capacity is at least 1000 we use 2 segments if 2 or more CPUs are available.
   * Segmenting the eviction only improves for lots of concurrent inserts or evictions,
   * there is no effect on read performance.
   */
  private Eviction constructEviction(HeapCache hc, HeapCacheListener l, Cache2kConfiguration config) {
    int _segmentCount = 1;
    if (Runtime.getRuntime().availableProcessors() > 1) {
      _segmentCount = 2;
    }
    if (config.getEvictionSegmentCount() > 0) {
      _segmentCount = config.getEvictionSegmentCount();
    }
    if (config.getEntryCapacity() < 1000) {
      _segmentCount = 1;
    }
    int _maxSegments = Runtime.getRuntime().availableProcessors() * 2;
    _segmentCount = Math.min(_segmentCount, _maxSegments);
    Eviction[] _segments = new Eviction[_segmentCount];
    long _maxSize = config.getEntryCapacity() / _segmentCount;
    if (config.getEntryCapacity() == Long.MAX_VALUE) {
      _maxSize = Long.MAX_VALUE;
    } else if (config.getEntryCapacity() % _segmentCount > 0) {
      _maxSize++;
    }
    for (int i = 0; i < _segments.length; i++) {
      Eviction ev = new ClockProPlusEviction(hc, l, _maxSize);
      _segments[i] = ev;
    }
    if (_segmentCount == 1) {
      return _segments[0];
    }
    return new SegmentedEviction(_segments);
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

    public AsyncCreatedListener(final AsyncDispatcher<K> _dispatcher, final CacheEntryCreatedListener<K, V> _listener) {
      dispatcher = _dispatcher;
      listener = _listener;
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

    public AsyncUpdatedListener(final AsyncDispatcher<K> _dispatcher, final CacheEntryUpdatedListener<K, V> _listener) {
      dispatcher = _dispatcher;
      listener = _listener;
    }

    @Override
    public void onEntryUpdated(final Cache<K, V> cache, final CacheEntry<K, V> currentEntry, final CacheEntry<K, V> entryWithNewData) {
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

    public AsyncRemovedListener(final AsyncDispatcher<K> _dispatcher, final CacheEntryRemovedListener<K, V> _listener) {
      dispatcher = _dispatcher;
      listener = _listener;
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

    public AsyncExpiredListener(final AsyncDispatcher<K> _dispatcher, final CacheEntryExpiredListener<K, V> _listener) {
      dispatcher = _dispatcher;
      listener = _listener;
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

}
