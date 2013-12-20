package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2013 headissue GmbH, Munich
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cache2k.Cache;
import org.cache2k.CacheManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Jens Wilke; created: 2013-07-01
 */
public class CacheManagerImpl extends CacheManager {
  

  static List<CacheLifeCycleListener> lifeCycleListeners = new ArrayList<>();

  static {
    lifeCycleListeners.add(new JmxSupport());
  }

  private Log log;
  private String name;
  private Map<String, BaseCache> cacheNames = new HashMap<>();
  private Set<Cache> caches = new HashSet<>();
  private int disambiguationCounter = 1;

  public CacheManagerImpl() {
    name = getDefaultName();
    log = LogFactory.getLog(CacheManager.class.getName() + '.' + name);
  }

  private void sendCreatedEvent(Cache c) {
    for (CacheLifeCycleListener e : lifeCycleListeners) {
      e.cacheCreated(this, c);
    }
  }

  private void sendDestroyedEvent(Cache c) {
    for (CacheLifeCycleListener e : lifeCycleListeners) {
      e.cacheDestroyed(this, c);
    }
  }

  /**
   * Don't accept a cache name with too weird characters. Rather then escaping the
   * name, so we can use it for JMX, it is better to just reject it.
   */
  private void checkName(String s) {
    for (char c : s.toCharArray()) {
      if (c == '.' ||
          c == '-') {
        continue;
      }
      if (!Character.isJavaIdentifierPart(c)) {
        throw new CacheUsageExcpetion(
          "Cache name contains illegal chars: '" + c + "', name=\"" + s + "\"");
      }
    }
  }

  /* called by builder */
  public synchronized void newCache(Cache c) {
    BaseCache bc = (BaseCache) c;
    String _requestedName = c.getName();
    String _name = _requestedName;
    while (cacheNames.containsKey(_name)) {
      _name = _requestedName + "$$" + (disambiguationCounter++);
    }
    if (!_requestedName.equals(_name)) {
      log.warn("duplicate name, disambiguating: " + _requestedName + " -> " + _name);
      bc.setName(_name);
    }
    checkName(_name);

    caches.add(c);
    sendCreatedEvent(c);
    bc.setCacheManager(this);
    cacheNames.put(c.getName(), bc);
  }

  /* called by cache or CM */
  public synchronized void cacheDestroyed(Cache c) {
    cacheNames.remove(c.getName());
    caches.remove(c);
    sendDestroyedEvent(c);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public Iterator<Cache> iterator() {
    return caches.iterator();
  }

  @Override
  public void clear() {
    for (Cache c : caches) {
      c.clear();
    }
  }

  @Override
  public void destroy() {
    for (Cache c : caches) {
      c.destroy();
    }
  }

}
