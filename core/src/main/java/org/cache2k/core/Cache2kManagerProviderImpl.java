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

import org.cache2k.CacheManager;
import org.cache2k.core.spi.CacheConfigurationProvider;
import org.cache2k.spi.Cache2kManagerProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke; created: 2015-03-26
 */
public class Cache2kManagerProviderImpl implements Cache2kManagerProvider {

  public final static String STANDARD_DEFAULT_MANAGER_NAME = "default";
  static final CacheConfigurationProvider CACHE_CONFIGURATION_PROVIDER =
    createConfigurationProvider();

  private Object lock = new Object();
  private volatile Map<ClassLoader, String> defaultCacheName = Collections.EMPTY_MAP;
  private volatile Map<ClassLoader, Map<String, CacheManager>> loader2name2manager = Collections.EMPTY_MAP;

  /**
   * Ignore linkage error, if there is no config module present.
   */
  private static CacheConfigurationProvider createConfigurationProvider() {
    try {
      return
        SingleProviderResolver.getInstance(CacheManagerImpl.class.getClassLoader())
          .resolve(CacheConfigurationProvider.class);
    } catch (LinkageError ex) {
      return null;
    }
  }

  public Object getLockObject() {
    return lock;
  }

  @Override
  public void setDefaultManagerName(ClassLoader cl, String s) {
    synchronized (getLockObject()) {
      if (defaultCacheName.containsKey(cl)) {
        throw new IllegalStateException("a CacheManager was already created");
      }
      defaultCacheName.put(cl, s);
    }
  }

  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    String n = defaultCacheName.get(cl);
    if (n != null) {
      return n;
    }
    synchronized (getLockObject()) {
      n = defaultCacheName.get(cl);
      if (n != null) {
        return n;
      }
      if (CACHE_CONFIGURATION_PROVIDER != null) {
        n = CACHE_CONFIGURATION_PROVIDER.getDefaultManagerName(cl);
      }
      if (n == null) {
        n = STANDARD_DEFAULT_MANAGER_NAME;
      }
      Map<ClassLoader, String> _copy = new HashMap<ClassLoader, String>(defaultCacheName);
      _copy.put(cl, n);
      defaultCacheName = _copy;
    }
    return n;
  }

  @Override
  public ClassLoader getDefaultClassLoader() {
    return getClass().getClassLoader();
  }

  @Override
  public CacheManager getManager(ClassLoader cl, String _name) {
    if (cl == null) {
      throw new NullPointerException("classloader is null");
    }
    return getManager(cl, _name, getDefaultManagerName(cl).equals(_name));
  }

  public CacheManager getManager(ClassLoader cl, String _name, boolean _default) {
    CacheManager mgr;
    Map<String, CacheManager> _loader2managers = loader2name2manager.get(cl);
    if (_loader2managers != null) {
      mgr = _loader2managers.get(_name);
      if (mgr != null) {
        return mgr;
      }
    }
    synchronized (getLockObject()) {
      _loader2managers = loader2name2manager.get(cl);
      if (_loader2managers != null) {
        mgr = _loader2managers.get(_name);
        if (mgr != null) {
          return mgr;
        }
      }
      if (_loader2managers != null) {
        _loader2managers = new HashMap<String, CacheManager>(_loader2managers);
      } else {
        _loader2managers = new HashMap<String, CacheManager>();
      }
      mgr = new CacheManagerImpl(cl, _name, _default);
      _loader2managers.put(_name, mgr);
      Map<ClassLoader, Map<String, CacheManager>> _copy =
        new WeakHashMap<ClassLoader, Map<String, CacheManager>>(loader2name2manager);
      _copy.put(cl, _loader2managers);
      loader2name2manager = _copy;
    }
    return mgr;
  }

  /**
   * Called from the manager after a close. Removes the manager from the known managers.
   */
  void removeManager(CacheManager cm) {
    synchronized (getLockObject()) {
      for (Map<String, CacheManager> m : loader2name2manager.values()) {
        Iterator<CacheManager> it = m.values().iterator();
        while (it.hasNext()) {
          CacheManager cm2 = it.next();
          if (cm == cm2) {
            it.remove();
          }
        }
      }
    }
  }

  @Override
  public void close(ClassLoader l) {
    Set<CacheManager> _managers = new HashSet<CacheManager>();
    Map<String, CacheManager> map;
    synchronized (getLockObject()) {
      map = loader2name2manager.get(l);
      if (map == null) {
        return;
      }
      _managers.addAll(map.values());
    }
    for (CacheManager cm : _managers) {
      cm.close();
    }
  }

  @Override
  public void close() {
    for (ClassLoader cl : loader2name2manager.keySet()) {
      close(cl);
    }
  }

  @Override
  public void close(ClassLoader l, String _name) {
    CacheManager cm;
    synchronized (getLockObject()) {
      Map<String, CacheManager> map = loader2name2manager.get(l);
      if (map == null) { return; }
      cm = map.get(_name);
      if (cm == null) { return; }
    }
    cm.close();
  }

}
