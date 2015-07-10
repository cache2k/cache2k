package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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

import org.cache2k.BulkCacheSource;
import org.cache2k.ExperimentalBulkCacheSource;
import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.cache2k.CacheConfig;
import org.cache2k.CacheManager;
import org.cache2k.CacheSource;
import org.cache2k.CacheSourceWithMetaInfo;
import org.cache2k.RefreshController;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * @author Jens Wilke; created: 2013-12-06
 */
@SuppressWarnings("unused") // instantiated by reflection from cache builder
public class CacheBuilderImpl<K, T> extends CacheBuilder<K, T> {

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
    if (RefreshController.class.isAssignableFrom(c)) { return refreshController; }
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
    if (refreshController != null) {
      c.setRefreshController(refreshController);
    }
    if (entryExpiryCalculator != null) {
      c.setEntryExpiryCalculator(entryExpiryCalculator);
    }
    if (exceptionExpiryCalculator != null) {
      c.setExceptionExpiryCalculator(exceptionExpiryCalculator);
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
    if (cacheWriter != null) {
      c.setWriter(cacheWriter);
    }
  }

  void configureViaSetters(Object o) {
    if (o instanceof BaseCache) {
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

  protected Cache<K,T> constructImplementationAndFillParameters(Class<?> cls) {
    if (!Cache.class.isAssignableFrom(cls)) {
      throw new IllegalArgumentException("Specified impl not a cache" + cls.getName());
    }
    try {
      Cache<K, T> _cache;
      Constructor<?> ctr = findConstructor(cls);
      if (ctr != null) {
        Class<?>[] pt = ctr.getParameterTypes();
        Object[] _args = new Object[pt.length];
        for (int i = 0; i < _args.length; i++) {
          _args[i] = getConstructorParameter(pt[i]);
        }
        _cache = (Cache<K, T>) ctr.newInstance(_args);
      } else {
        _cache = (Cache<K, T>) cls.newInstance();
      }
      return _cache;
    } catch (Exception e) {
      throw new IllegalArgumentException("Not able to instantiate cache implementation", e);
    }
  }

  public Cache<K, T> build() {
    config = createConfiguration();
    if (config.getName() == null) {
      config.setName(deriveNameFromStackTrace());
    }
    Class<?> _implClass = BaseCache.TUNABLE.defaultImplementation;
    if (config.getImplementation() != null) {
      _implClass = config.getImplementation();
    }
    Cache<K,T> _cache = constructImplementationAndFillParameters(_implClass);
    CacheManagerImpl cm = null;
    if (_cache instanceof BaseCache) {
      cm = (CacheManagerImpl) (manager == null ? CacheManager.getInstance() : manager);
      ((BaseCache) _cache).setCacheManager(cm);
    }
    configureViaSetters(_cache);
    if (cm != null) {
      cm.newCache(_cache);
    }
    if (_cache instanceof BaseCache) {
      ((BaseCache) _cache).init();
    }
    return _cache;
  }

}
