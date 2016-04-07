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
import org.cache2k.impl.BaseCache;
import org.cache2k.impl.CacheLifeCycleListener;
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.CacheManagerLifeCycleListener;
import org.cache2k.impl.CacheUsageExcpetion;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * Adds optional support for JMX.
 */
public class JmxSupport implements CacheLifeCycleListener, CacheManagerLifeCycleListener {

  @Override
  public void cacheCreated(CacheManager cm, Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof BaseCache) {
      String _name = standardName(cm, c);
      try {
         mbs.registerMBean(
           new CacheMXBeanImpl((BaseCache) c),
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

  @Override
  public void managerCreated(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerName(m);
    try {
      mbs.registerMBean(new ManagerMXBeanImpl((CacheManagerImpl) m),new ObjectName(_name));
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
    }
  }

  @Override
  public void managerDestroyed(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = cacheManagerName(m);
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

  private static String standardName(CacheManager cm, Cache c) {
    return
      "org.cache2k" + ":" +
      "type=Cache" +
      ",manager=" + cm.getName() +
      ",name=" + c.getName();
  }

}
