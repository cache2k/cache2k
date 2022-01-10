package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.spi.CacheConfigProvider;
import org.cache2k.core.util.Cache2kVersion;
import org.cache2k.core.log.Log;
import org.cache2k.core.util.TunableConstants;
import org.cache2k.core.util.TunableFactory;
import org.cache2k.spi.Cache2kCoreProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke
 */
public class Cache2kCoreProviderImpl implements Cache2kCoreProvider {

  public static final CacheConfigProvider CACHE_CONFIGURATION_PROVIDER =
    TunableFactory.get(Tunable.class).enableExternalConfiguration ?
    SingleProviderResolver.resolve(
      CacheConfigProvider.class, DummyConfigProvider.class) :
      new DummyConfigProvider();

  private final Object lock = new Object();
  private volatile Map<ClassLoader, String> loader2defaultName = Collections.emptyMap();
  private volatile Map<ClassLoader, Map<String, CacheManager>> loader2name2manager =
    Collections.emptyMap();
  private String version;

  {
    extractVersionAndGreet();
  }

  private void extractVersionAndGreet() {
    Log log = Log.getLog(this.getClass());
    version = Cache2kVersion.getVersion();
    StringBuilder sb = new StringBuilder();
    sb.append("cache2k starting. ");
    sb.append("version=");
    sb.append(version);
    log.info(sb.toString());
    String message = TunableFactory.get(Tunable.class).message;
    if (message != null) {
      log.info(message);
    }
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
      replaceDefaultName(cl, s);
    }
  }

  @Override
  public String getDefaultManagerName(ClassLoader cl) {
    String n = loader2defaultName.get(cl);
    if (n != null) {
      return n;
    }
    synchronized (getLockObject()) {
      n = CACHE_CONFIGURATION_PROVIDER.getDefaultManagerName(cl);
      if (n == null) {
        n = CacheManager.STANDARD_DEFAULT_MANAGER_NAME;
      }
      replaceDefaultName(cl, n);
    }
    return n;
  }

  private void replaceDefaultName(ClassLoader cl, String s) {
    Map<ClassLoader, String> copy = new WeakHashMap<>(loader2defaultName);
    copy.put(cl, s);
    loader2defaultName = copy;
  }

  @Override
  public ClassLoader getDefaultClassLoader() {
    return getClass().getClassLoader();
  }

  @Override
  public CacheManager getManager(ClassLoader cl, String name) {
    CacheManagerImpl.checkName(name);
    if (cl == null) {
      throw new NullPointerException("classloader is null");
    }
    return getManager(cl, name, getDefaultManagerName(cl).equals(name));
  }

  public CacheManager getManager(ClassLoader cl, String name, boolean defaultFallback) {
    CacheManager mgr;
    Map<String, CacheManager> loader2managers = loader2name2manager.get(cl);
    if (loader2managers != null) {
      mgr = loader2managers.get(name);
      if (mgr != null) {
        return mgr;
      }
    }
    synchronized (getLockObject()) {
      loader2managers = loader2name2manager.get(cl);
      if (loader2managers != null) {
        mgr = loader2managers.get(name);
        if (mgr != null) {
          return mgr;
        }
      }
      if (loader2managers != null) {
        loader2managers = new HashMap<>(loader2managers);
      } else {
        loader2managers = new HashMap<>();
      }
      mgr = new CacheManagerImpl(this, cl, name, defaultFallback);
      loader2managers.put(name, mgr);
      Map<ClassLoader, Map<String, CacheManager>> copy =
        new WeakHashMap<>(loader2name2manager);
      copy.put(cl, loader2managers);
      loader2name2manager = copy;
    }
    return mgr;
  }

  /**
   * Called from the manager after a close. Removes the manager from the known managers.
   */
  void removeManager(CacheManager cm) {
    synchronized (getLockObject()) {
      Map<String, CacheManager> name2managers = loader2name2manager.get(cm.getClassLoader());
      name2managers = new HashMap<>(name2managers);
      Object removed = name2managers.remove(cm.getName());
      Map<ClassLoader, Map<String, CacheManager>> copy =
        new WeakHashMap<>(loader2name2manager);
      copy.put(cm.getClassLoader(), name2managers);
      loader2name2manager = copy;
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
  public <K, V> Cache<K, V> createCache(CacheManager m, Cache2kConfig<K, V> cfg) {
    return new InternalCache2kBuilder<>(cfg, m).build();
  }

  public String getVersion() {
    return version;
  }

  public Cache2kConfig getDefaultConfig(CacheManager mgr) {
    return CACHE_CONFIGURATION_PROVIDER.getDefaultConfig(mgr);
  }

  public static class Tunable extends TunableConstants {

    public boolean enableExternalConfiguration = true;
    public String message = null;

  }

}
