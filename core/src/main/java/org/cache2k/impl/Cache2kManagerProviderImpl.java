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

  public final static String DEFAULT_MANAGER_NAME = "default";

  CacheManager defaultManager;
  String defaultName = DEFAULT_MANAGER_NAME;
  Map<ClassLoader, Map<String, CacheManager>> loader2name2manager =
      new WeakHashMap<ClassLoader, Map<String, CacheManager>>();

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
  public synchronized CacheManager getManager(ClassLoader cl, String _name, Properties p) {
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
    synchronized (this) {
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
    synchronized (this) {
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
    synchronized (this) {
      Map<String, CacheManager> map = loader2name2manager.get(l);
      if (map == null) { return; }
      cm = map.get(_name);
      if (cm == null) { return; }
    }
    cm.close();
  }

}
