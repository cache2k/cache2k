package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
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

import org.cache2k.Cache;
import org.cache2k.CacheManager;
import org.cache2k.core.CacheLifeCycleListener;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.core.CacheManagerLifeCycleListener;
import org.cache2k.core.CacheUsageExcpetion;
import org.cache2k.core.InternalCache;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Adds optional support for JMX.
 */
public class JmxSupport implements CacheLifeCycleListener, CacheManagerLifeCycleListener {

  int seenClassLoaderCount = 1;
  Map<ClassLoader, Integer> classLoader2Integer = new WeakHashMap<ClassLoader, Integer>();

  @Override
  public void cacheCreated(CacheManager cm, Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof InternalCache) {
      String _name = standardName(cm, c);
      try {
         mbs.registerMBean(
           new CacheMXBeanImpl((InternalCache) c),
           new ObjectName(_name));
      } catch (Exception e) {
        throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
      }
    }
  }

  @Override
  public void cacheDestroyed(CacheManager cm, Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof InternalCache) {
      String _name = standardName(cm, c);
      try {
        mbs.unregisterMBean(new ObjectName(_name));
      } catch (InstanceNotFoundException ignore) {
      } catch (Exception e) {
        throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
      }
    }
  }

  @Override
  public void managerCreated(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerNameWithClassLoader(m);
    try {
      mbs.registerMBean(new ManagerMXBeanImpl((CacheManagerImpl) m),new ObjectName(_name));
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
    }
  }

  @Override
  public void managerDestroyed(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerNameWithClassLoader(m);
    try {
      mbs.unregisterMBean(new ObjectName(_name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
    }
  }

  private static String cacheManagerName(CacheManager cm) {
    return
      "org.cache2k" + ":" +
      "type=CacheManager" +
      ",name=" + cm.getName();
  }

  private static String cacheManagerNameWithClassLoaderNumber(CacheManager cm, int _classLoaderNumber) {
    return
      "org.cache2k" + ":" +
      "type=CacheManager" +
      ",name=" + cm.getName() +
      ",uniqueClassLoaderNumber=" + _classLoaderNumber;
  }

  /**
   * JSR107 allows cache managers with identical names within different class loaders.
   * If multiple class loaders are involved, we need to add a qualifier to separate the names.
   */
  private synchronized int getUniqueClassLoaderNumber(ClassLoader _classLoader) {
    Integer no = classLoader2Integer.get(_classLoader);
    if (no == null) {
      no = seenClassLoaderCount++;
      classLoader2Integer.put(_classLoader, no);
    }
    return no;
  }

  private synchronized String cacheManagerNameWithClassLoader(CacheManager cm) {
    ClassLoader _classLoader = cm.getClassLoader();
    int no = getUniqueClassLoaderNumber(_classLoader);
    if (no == 1) {
      return cacheManagerName(cm);
    }
    return cacheManagerNameWithClassLoaderNumber(cm, no);
  }

  private synchronized String standardName(CacheManager cm, Cache c) {
    int _classLoaderNumber = getUniqueClassLoaderNumber(cm.getClassLoader());
    if (_classLoaderNumber == 1) {
      return
          "org.cache2k" + ":" +
              "type=Cache" +
              ",manager=" + cm.getName() +
              ",name=" + c.getName();
    }
    return
        "org.cache2k" + ":" +
        "type=Cache" +
        ",manager=" + cm.getName() +
        ",uniqueClassLoaderNumber=" + _classLoaderNumber +
        ",name=" + c.getName();

  }

}
