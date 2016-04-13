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

import org.cache2k.BulkCacheSource;
import org.cache2k.event.CacheEntryCreatedListener;
import org.cache2k.event.CacheEntryOperationListener;
import org.cache2k.event.CacheEntryRemovedListener;
import org.cache2k.event.CacheEntryUpdatedListener;
import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheConfig;
import org.cache2k.CacheManager;
import org.cache2k.CacheSource;
import org.cache2k.CacheSourceWithMetaInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Jens Wilke; created: 2013-12-06
 */
@SuppressWarnings("unused") // instantiated by reflection from cache builder
public class Cache2kBuilderImpl<K, T> extends Cache2kBuilder<K, T> {

  List<CacheEntryOperationListener<K,T>> syncListeners;

  @Override
  public Cache2kBuilder<K, T> addListener(final CacheEntryOperationListener<K, T> listener) {
    if (syncListeners == null) {
      syncListeners = new ArrayList<CacheEntryOperationListener<K, T>>();
    }
    syncListeners.add(listener);
    return this;
  }

  String deriveNameFromStackTrace() {
    Exception ex = new Exception();
    for (StackTraceElement e : ex.getStackTrace()) {
      if (!e.getClassName().startsWith(this.getClass().getPackage().getName())) {
        int idx = e.getClassName().lastIndexOf('.');
        String _simpleClassName = e.getClassName().substring(idx + 1);
        String _methodName = e.getMethodName();
        if (_methodName.equals("<init>")) {
          _methodName = "INIT";
        }
        if (_methodName != null && _methodName.length() > 0) {
          return _simpleClassName + "." + _methodName + "" + "." + e.getLineNumber();
        }
      }
    }
    return null;
  }

  Object getConstructorParameter(Class<?> c) {
    if (CacheConfig.class.isAssignableFrom(c)) { return config; }
    if (CacheSource.class.isAssignableFrom(c)) { return cacheSource; }
    if (CacheSourceWithMetaInfo.class.isAssignableFrom(c)) { return cacheSourceWithMetaInfo; }
    if (ExperimentalBulkCacheSource.class.isAssignableFrom(c)) { return experimentalBulkCacheSource; }
    if (BulkCacheSource.class.isAssignableFrom(c)) { return bulkCacheSource; }
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
    if (cacheSource != null) {
      c.setSource(cacheSource);
    }
    if (cacheSourceWithMetaInfo != null) {
      c.setSource(cacheSourceWithMetaInfo);
    }
    if (config.getLoader() != null) {
      c.setLoader(config.getLoader());
    }
    if (config.getAdvancedLoader() != null) {
      c.setAdvancedLoader(config.getAdvancedLoader());
    }
    if (exceptionPropagator != null) {
      c.setExceptionPropagator(exceptionPropagator);
    }
    if (config != null) {
      c.setCacheConfig(config);
    }
    if (bulkCacheSource != null) {
      c.setBulkCacheSource(bulkCacheSource);
    }
    if (experimentalBulkCacheSource != null) {
      c.setExperimentalBulkCacheSource(experimentalBulkCacheSource);
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
    config = createConfiguration();
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
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

    if (syncListeners != null) { _wrap = true; }
    if (cacheWriter != null) { _wrap = true; }

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
      if (cacheWriter != null) {
        wc.writer = cacheWriter;
      }
      if (syncListeners != null) {
        List<CacheEntryCreatedListener<K,T>> cll = new ArrayList<CacheEntryCreatedListener<K, T>>();
        List<CacheEntryUpdatedListener<K,T>> ull = new ArrayList<CacheEntryUpdatedListener<K, T>>();
        List<CacheEntryRemovedListener<K,T>> rll = new ArrayList<CacheEntryRemovedListener<K, T>>();
        for (CacheEntryOperationListener<K,T> el : syncListeners) {
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
