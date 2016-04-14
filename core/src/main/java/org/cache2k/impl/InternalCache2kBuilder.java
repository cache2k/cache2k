package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.Cache;
import org.cache2k.CacheConfig;
import org.cache2k.CacheManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Method object to construct a cache2k cache.
 *
 * @author Jens Wilke; created: 2013-12-06
 */
public class InternalCache2kBuilder<K, T> {

  static final AtomicLong DERIVED_NAME_COUNTER =
    new AtomicLong(System.currentTimeMillis() % 1234);

  CacheManager manager;
  CacheConfig<K,T> config;

  public InternalCache2kBuilder(final CacheConfig<K, T> _config, final CacheManager _manager) {
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
    if (CacheConfig.class.isAssignableFrom(c)) { return config; }
    return null;
  }

  /** return the first constructor with CacheConfig as first parameter */
  Constructor<?> findConstructor(Class<?> c) {
    for (Constructor ctr : c.getConstructors()) {
      Class<?>[] pt = ctr.getParameterTypes();
      if (pt != null && pt.length > 0 && CacheConfig.class.isAssignableFrom(pt[0])) {
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
  void confiugreViaSettersDirect(BaseCache c) {
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
      confiugreViaSettersDirect((BaseCache) o);
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
    Class<?> _implClass = BaseCache.TUNABLE.defaultImplementation;
    if (config.getImplementation() != null) {
      _implClass = config.getImplementation();
    }
    InternalCache<K,T> _cache = constructImplementationAndFillParameters(_implClass);

    BaseCache bc = (BaseCache) _cache;
    CacheManagerImpl cm = (CacheManagerImpl) (manager == null ? CacheManager.getInstance() : manager);
    bc.setCacheManager(cm);
    configureViaSetters(bc);

    boolean _wrap = false;

    if (config.hasListeners()) { _wrap = true; }
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
      if (config.hasListeners()) {
        List<CacheEntryCreatedListener<K,T>> cll = new ArrayList<CacheEntryCreatedListener<K, T>>();
        List<CacheEntryUpdatedListener<K,T>> ull = new ArrayList<CacheEntryUpdatedListener<K, T>>();
        List<CacheEntryRemovedListener<K,T>> rll = new ArrayList<CacheEntryRemovedListener<K, T>>();
        for (CacheEntryOperationListener<K,T> el : config.getListeners()) {
          if (el instanceof CacheEntryCreatedListener) {
            cll.add((CacheEntryCreatedListener) el);
          }
          if (el instanceof CacheEntryUpdatedListener) {
            ull.add((CacheEntryUpdatedListener) el);
          }
          if (el instanceof CacheEntryRemovedListener) {
            rll.add((CacheEntryRemovedListener) el);
          }
        }
        if (!cll.isEmpty()) {
          wc.syncEntryCreatedListeners = cll.toArray(new CacheEntryCreatedListener[cll.size()]);
        }
        if (!ull.isEmpty()) {
          wc.syncEntryUpdatedListeners = ull.toArray(new CacheEntryUpdatedListener[ull.size()]);
        }
        if (!rll.isEmpty()) {
          wc.syncEntryRemovedListeners = rll.toArray(new CacheEntryRemovedListener[rll.size()]);
        }
      }
      bc.listener = wc;
      RefreshHandler<K,T> rh =RefreshHandler.of(config);
      wc.refreshHandler = rh;
      wc.init();
    } else {
      bc.setRefreshHandler(RefreshHandler.of(config));
      bc.init();
    }
    return _cache;
  }

}
