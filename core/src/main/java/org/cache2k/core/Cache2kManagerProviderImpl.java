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
import org.cache2k.spi.Cache2kManagerProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Jens Wilke; created: 2015-03-26
 */
public class Cache2kManagerProviderImpl implements Cache2kManagerProvider {

  final static String DEFAULT_MANAGER_NAME = "default";

  private CacheManager defaultManager;
  private String defaultName = DEFAULT_MANAGER_NAME;
  private Map<ClassLoader, Map<String, CacheManager>> loader2name2manager =
      new WeakHashMap<ClassLoader, Map<String, CacheManager>>();

  public Object getLockObject() {
    return loader2name2manager;
  }

  @Override
  public void setDefaultName(String s) {
    if (defaultManager != null) {
      throw new IllegalStateException("default CacheManager already created");
    }
    defaultName = s;
  }

  @Override
  public String getDefaultName() {
    return defaultName;
  }

  @Override
  public ClassLoader getDefaultClassLoader() {
    return getClass().getClassLoader();
  }

  @Override
  public CacheManager getManager(ClassLoader cl, String _name, Properties p) {
    synchronized (getLockObject()) {
      if (cl == null) {
        cl = getDefaultClassLoader();
      }
      Map<String, CacheManager> map = loader2name2manager.get(cl);
      if (map == null) {
        loader2name2manager.put(cl, map = new HashMap<String, CacheManager>());
      }
      CacheManager cm = map.get(_name);
      if (cm == null) {
        cm = new CacheManagerImpl(cl, _name, p);
        map.put(_name, cm);
      }
      return cm;
    }
  }

  @Override
  public CacheManager getDefaultManager(Properties p) {
    if (defaultManager != null) {
      return defaultManager;
    }
    return defaultManager = getManager(getDefaultClassLoader(), getDefaultName(), p);
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
