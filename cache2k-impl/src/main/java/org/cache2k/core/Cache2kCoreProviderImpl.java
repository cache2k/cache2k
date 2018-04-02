package org.cache2k.core;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.core.spi.CacheConfigurationProvider;
import org.cache2k.core.util.Cache2kVersion;
import org.cache2k.core.util.Log;
import org.cache2k.spi.Cache2kCoreProvider;
import org.cache2k.spi.SingleProviderResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke; created: 2015-03-26
 */
public class Cache2kCoreProviderImpl implements Cache2kCoreProvider {

  public final static String STANDARD_DEFAULT_MANAGER_NAME = "default";
  public static final CacheConfigurationProvider CACHE_CONFIGURATION_PROVIDER =
    SingleProviderResolver.resolve(CacheConfigurationProvider.class);

  public static <K,V> void augmentConfiguration(CacheManager mgr, Cache2kConfiguration<K,V> cfg) {
    if (CACHE_CONFIGURATION_PROVIDER != null) {
      CACHE_CONFIGURATION_PROVIDER.augmentConfiguration(mgr, cfg);
    }
  }

  private Object lock = new Object();
  private volatile Map<ClassLoader, String> loader2defaultName = Collections.EMPTY_MAP;
  private volatile Map<ClassLoader, Map<String, CacheManager>> loader2name2manager = Collections.EMPTY_MAP;
  private String version;
  private String buildNumber;

  {
    Log log = Log.getLog(this.getClass());
    buildNumber = Cache2kVersion.getBuildNumber();
    version = Cache2kVersion.getVersion();
    StringBuilder sb = new StringBuilder();
    sb.append("cache2k starting. ");
    sb.append("version=");
    sb.append(version);
    sb.append(", build=");
    sb.append(buildNumber);
    sb.append(", defaultImplementation=");
    sb.append(HeapCache.TUNABLE.defaultImplementation.getSimpleName());
    log.info(sb.toString());
  }

  public Object getLockObject() {
    return lock;
  }

  @Override
  public void setDefaultManagerName(ClassLoader cl, String s) {
    synchronized (getLockObject()) {
      if (loader2defaultName.containsKey(cl)) {
        throw new IllegalStateException("a CacheManager was already created");
      }
      loader2defaultName.put(cl, s);
    }
  }

  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    String n = loader2defaultName.get(cl);
    if (n != null) {
      return n;
    }
    synchronized (getLockObject()) {
      n = loader2defaultName.get(cl);
      if (n != null) {
        return n;
      }
      if (CACHE_CONFIGURATION_PROVIDER != null) {
        n = CACHE_CONFIGURATION_PROVIDER.getDefaultManagerName(cl);
      }
      if (n == null) {
        n = STANDARD_DEFAULT_MANAGER_NAME;
      }
      Map<ClassLoader, String> _copy = new WeakHashMap<ClassLoader, String>(loader2defaultName);
      _copy.put(cl, n);
      loader2defaultName = _copy;
    }
    return n;
  }

  @Override
  public ClassLoader getDefaultClassLoader() {
    return getClass().getClassLoader();
  }

  @Override
  public CacheManager getManager(ClassLoader cl, String _name) {
    CacheManagerImpl.checkName(_name);
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
      mgr = new CacheManagerImpl(this, cl, _name, _default);
      _loader2managers.put(_name, mgr);
      Map<ClassLoader, Map<String, CacheManager>> _copy =  new WeakHashMap<ClassLoader, Map<String, CacheManager>>(loader2name2manager);
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
      Map<String, CacheManager> _name2managers = loader2name2manager.get(cm.getClassLoader());
      _name2managers = new HashMap<String, CacheManager>(_name2managers);
      Object _removed = _name2managers.remove(cm.getName());
      Map<ClassLoader, Map<String, CacheManager>> _copy = new WeakHashMap<ClassLoader, Map<String, CacheManager>>(loader2name2manager);
      _copy.put(cm.getClassLoader(), _name2managers);
      loader2name2manager = _copy;
      if (cm.isDefaultManager()) {
        Map<ClassLoader, String> _defaultNameCopy = new WeakHashMap<ClassLoader, String>(loader2defaultName);
        _defaultNameCopy.remove(cm.getClassLoader());
        loader2defaultName = _defaultNameCopy;
      }
    }
  }

  @Override
  public void close(ClassLoader l) {
    for (CacheManager cm : loader2name2manager.get(l).values()) {
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
  public void close(ClassLoader cl, String managerName) {
    if (cl == null) {
      cl = getDefaultClassLoader();
    }
    CacheManager cm;
    Map<String, CacheManager> map = loader2name2manager.get(cl);
    if (map == null) { return; }
    cm = map.get(managerName);
    if (cm == null) { return; }
    cm.close();
  }

  @Override
  public <K, V> Cache<K, V> createCache(final CacheManager m, final Cache2kConfiguration<K, V> cfg) {
    return new InternalCache2kBuilder<K,V>(cfg, m).build();
  }

  public String getVersion() {
    return version;
  }

  public String getBuildNumber() {
    return buildNumber;
  }

  public Cache2kConfiguration getDefaultConfiguration(CacheManager mgr) {
    if (CACHE_CONFIGURATION_PROVIDER != null) {
      return CACHE_CONFIGURATION_PROVIDER.getDefaultConfiguration(mgr);
    }
    return new Cache2kConfiguration();
  }

}
