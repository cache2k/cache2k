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

import org.cache2k.Cache;
import org.cache2k.CacheManager;
import org.cache2k.jmx.CacheManagerMXBean;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * This
 */
public class JmxSupport implements CacheLifeCycleListener {

  @Override
  public void cacheCreated(CacheManager cm, Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof BaseCache) {
      String _name = standardName(cm, c);
      try {
        mbs.registerMBean(((BaseCache) c).getMXBean(),
          new ObjectName(_name));
      } catch (Exception e) {
        throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
      }
    }
  }

  @Override
  public void cacheDestroyed(CacheManager cm, Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof BaseCache) {
      String _name = standardName(cm, c);
      try {
        mbs.unregisterMBean(new ObjectName(_name));
      } catch (InstanceNotFoundException ignore) {
      } catch (Exception e) {
        throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
      }
    }
  }

  public void registerManager(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerName(m);
    try {
      mbs.registerMBean(new ManagerMBean((CacheManagerImpl) m),new ObjectName(_name));
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
    }
  }

  public void unregisterManager(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerName(m);
    try {
      mbs.unregisterMBean(new ObjectName(_name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
    }
  }


  public static class ManagerMBean implements CacheManagerMXBean {

    CacheManagerImpl manager;

    public ManagerMBean(CacheManagerImpl manager) {
      this.manager = manager;
    }

    @Override
    public int getAlert() {
      int v = 0;
      for (Cache c : manager) {
        if (c instanceof BaseCache) {
          v = Math.max(v, ((BaseCache) c).getInfo().getHealth());
        }
      }
      return v;
    }

    @Override
    public void clear() {
      manager.clear();
    }
  }


  private static String cacheManagerName(CacheManager cm) {
    return
      "com.cache2k" + ":" +
      "type=CacheManager" +
      ",name=" + cm.getName();
  }

  private static String standardName(CacheManager cm, Cache c) {
    return
      "com.cache2k" + ":" +
      "type=Cache" +
      ",manager=" + cm.getName() +
      ",name=" + c.getName();
  }


}
