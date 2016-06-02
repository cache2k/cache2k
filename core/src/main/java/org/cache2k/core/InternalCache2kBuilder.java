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
import org.cache2k.core.operation.ReadOnlyCacheEntry;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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
public class InternalCache2kBuilder<K, T> {

  static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);
  static final ThreadPoolExecutor ASYNC_EXECUTOR =
    new ThreadPoolExecutor(
      Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
      21, TimeUnit.SECONDS,
      new LinkedBlockingDeque<Runnable>(),
      HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory("cache2k-async"),
      new ThreadPoolExecutor.AbortPolicy());

  CacheManager manager;
  Cache2kConfiguration<K,T> config;

  public InternalCache2kBuilder(final Cache2kConfiguration<K, T> _config, final CacheManager _manager) {
    config = _config;
    manager = _manager;
  }

  boolean isBuilderClass(String _className) {
    return CacheBuilder.class.getName().equals(_className) ||
        Cache2kBuilder.class.getName().equals(_className);
  }

  String deriveNameFromStackTrace() {
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
    return null;
  }

  Object getConstructorParameter(Class<?> c) {
    if (Cache2kConfiguration.class.isAssignableFrom(c)) { return config; }
    return null;
  }

  /** return the first constructor with CacheConfig as first parameter */
  Constructor<?> findConstructor(Class<?> c) {
    for (Constructor ctr : c.getConstructors()) {
      Class<?>[] pt = ctr.getParameterTypes();
      if (pt != null && pt.length > 0 && Cache2kConfiguration.class.isAssignableFrom(pt[0])) {
        return ctr;
      }
    }
    return null;
  }

  /**
   * The generic wiring code is not working on android.
   * Explicitly call the wiring methods.
   */
  @SuppressWarnings("unchecked")
  void confiugreViaSettersDirect(HeapCache c) {
    if (config.getLoader() != null) {
      c.setLoader(config.getLoader());
    }
    if (config.getAdvancedLoader() != null) {
      c.setAdvancedLoader(config.getAdvancedLoader());
    }
    if (config.getExceptionPropagator() != null) {
      c.setExceptionPropagator(config.getExceptionPropagator());
    }
    if (config != null) {
      c.setCacheConfig(config);
    }
  }

  void configureViaSetters(Object o) {
    if (o instanceof InternalCache) {
      confiugreViaSettersDirect((HeapCache) o);
      return;
    }
    try {
      for (Method m : o.getClass().getMethods()) {
        Class<?>[] ps = m.getParameterTypes();
        if (ps != null && ps.length == 1 && m.getName().startsWith(("set"))) {
          Object p = getConstructorParameter(ps[0]);
          if (p != null) {
            m.invoke(o, p);
          }
        }
      }
    } catch (Exception ex) {
      throw new IllegalArgumentException("Unable to configure cache", ex);
    }
  }

  protected InternalCache<K,T> constructImplementationAndFillParameters(Class<?> cls) {
    if (!InternalCache.class.isAssignableFrom(cls)) {
      throw new IllegalArgumentException("Specified impl not a cache" + cls.getName());
    }
    try {
      InternalCache<K, T> _cache;
      Constructor<?> ctr = findConstructor(cls);
      if (ctr != null) {
        Class<?>[] pt = ctr.getParameterTypes();
        Object[] _args = new Object[pt.length];
        for (int i = 0; i < _args.length; i++) {
          _args[i] = getConstructorParameter(pt[i]);
        }
        _cache = (InternalCache<K, T>) ctr.newInstance(_args);
      } else {
        _cache = (InternalCache<K, T>) cls.newInstance();
      }
      return _cache;
    } catch (Exception e) {
      throw new IllegalArgumentException("Not able to instantiate cache implementation", e);
    }
  }

  @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
  public Cache<K, T> build() {
    if (config.getValueType() == null) {
      config.setValueType(Object.class);
    }
    if (config.getKeyType() == null) {
      config.setKeyType(Object.class);
    }
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
      if (config.getName() == null) {
        throw new IllegalArgumentException("name missing and automatic generation failed");
      }
    }
    checkConfiguration();
    Class<?> _implClass = HeapCache.TUNABLE.defaultImplementation;
    InternalCache<K,T> _cache = constructImplementationAndFillParameters(_implClass);

    HeapCache bc = (HeapCache) _cache;
    CacheManagerImpl cm = (CacheManagerImpl) (manager == null ? CacheManager.getInstance() : manager);
    bc.setCacheManager(cm);
    configureViaSetters(bc);

    boolean _wrap = false;

    if (config.hasListeners()) { _wrap = true; }
    if (config.hasAsyncListeners()) { _wrap = true; }
    if (config.getWriter() != null) { _wrap = true; }

    WiredCache<K, T> wc = null;
    if (_wrap) {
      wc = new WiredCache<K, T>();
      wc.heapCache = bc;
      _cache = wc;
    }

    String _name = cm.newCache(_cache, bc.getName());
    bc.setName(_name);
    if (_wrap) {
      wc.loader = bc.loader;
      if (config.getWriter() != null) {
        wc.writer = config.getWriter();
      }
      List<CacheEntryCreatedListener<K,T>> _syncCreatedListeners = new ArrayList<CacheEntryCreatedListener<K, T>>();
      List<CacheEntryUpdatedListener<K,T>> _syncUpdatedListeners = new ArrayList<CacheEntryUpdatedListener<K, T>>();
      List<CacheEntryRemovedListener<K,T>> _syncRemovedListeners = new ArrayList<CacheEntryRemovedListener<K, T>>();
      List<CacheEntryExpiredListener<K,T>> _syncExpiredListeners = new ArrayList<CacheEntryExpiredListener<K, T>>();
      if (config.hasListeners()) {
        for (CacheEntryOperationListener<K,T> el : config.getListeners()) {
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
            config.getAsyncListeners().add(el);
          }
        }
      }
      if (config.hasAsyncListeners()) {
        AsyncDispatcher<K> _asyncDispatcher = new AsyncDispatcher<K>(wc, ASYNC_EXECUTOR);
        List<CacheEntryCreatedListener<K,T>> cll = new ArrayList<CacheEntryCreatedListener<K, T>>();
        List<CacheEntryUpdatedListener<K,T>> ull = new ArrayList<CacheEntryUpdatedListener<K, T>>();
        List<CacheEntryRemovedListener<K,T>> rll = new ArrayList<CacheEntryRemovedListener<K, T>>();
        List<CacheEntryExpiredListener<K,T>> ell = new ArrayList<CacheEntryExpiredListener<K, T>>();
        for (CacheEntryOperationListener<K,T> el : config.getAsyncListeners()) {
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
          _syncCreatedListeners.add(new AsyncCreatedListener<K, T>(_asyncDispatcher, l));
        }
        for (CacheEntryUpdatedListener l : ull) {
          _syncUpdatedListeners.add(new AsyncUpdatedListener<K, T>(_asyncDispatcher, l));
        }
        for (CacheEntryRemovedListener l : rll) {
          _syncRemovedListeners.add(new AsyncRemovedListener<K, T>(_asyncDispatcher, l));
        }
        for (CacheEntryExpiredListener l : ell) {
          _syncExpiredListeners.add(new AsyncExpiredListener<K, T>(_asyncDispatcher, l));
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
    cm.sendCreatedEvent(_cache);
    return _cache;
  }

  Eviction constructEviction(HeapCache hc, HeapCacheListener l, Cache2kConfiguration config) {
    boolean _queue = false;
    int _segmentCount = 2;
    if (config.getEntryCapacity() < 1000) {
      _segmentCount = 1;
    }
    _segmentCount = 1;
    Eviction[] _segments = new Eviction[_segmentCount];
    for (int i = 0; i < _segments.length; i++) {
      Eviction ev = new ClockProPlusEviction(hc, l, config, _segmentCount);
      if (_queue) {
        ev = new QueuedEviction((AbstractEviction) ev);
      }
      _segments[i] = ev;
    }
    if (_segmentCount == 1) {
      return _segments[0];
    }
    return new SegmentedEviction(_segments);
  }

  void checkConfiguration() {
    if (config.isRefreshAhead() && !config.isKeepDataAfterExpired()) {
      throw new IllegalArgumentException("refreshAhead && !keepDataAfterExpired");
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

  static class AsyncUpdatedListener<K,V> implements CacheEntryUpdatedListener<K,V> {
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

  static class AsyncRemovedListener<K,V> implements CacheEntryRemovedListener<K,V> {
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

  static class AsyncExpiredListener<K,V> implements CacheEntryExpiredListener<K,V> {
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
