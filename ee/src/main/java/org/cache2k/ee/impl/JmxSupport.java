package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheManager;
import org.cache2k.impl.CacheLifeCycleListener;
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.CacheManagerLifeCycleListener;
import org.cache2k.impl.CacheUsageExcpetion;
import org.cache2k.impl.InternalCache;

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
