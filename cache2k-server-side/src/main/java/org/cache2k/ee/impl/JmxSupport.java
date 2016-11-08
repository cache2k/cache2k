package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k server side
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
import org.cache2k.CacheException;
import org.cache2k.CacheManager;
import org.cache2k.core.spi.CacheLifeCycleListener;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.core.InternalCache;
import org.cache2k.core.spi.CacheManagerLifeCycleListener;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

/**
 * Adds optional support for JMX.
 *
 * <p>Registering a name may fail because cache manager names may be identical in different
 * class loaders.
 */
public class JmxSupport implements CacheLifeCycleListener, CacheManagerLifeCycleListener {

  @Override
  public void cacheCreated(Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof InternalCache) {
      String _name = standardName(c.getCacheManager(), c);
      try {
        mbs.registerMBean(
          new CacheMXBeanImpl((InternalCache) c),
          new ObjectName(_name));
      } catch (InstanceAlreadyExistsException ignore) {
      } catch (Exception e) {
        throw new CacheException("register JMX bean, ObjectName: " + _name, e);
      }
    }
  }

  @Override
  public void cacheDestroyed(Cache c) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    if (c instanceof InternalCache) {
      String _name = standardName(c.getCacheManager(), c);
      try {
        mbs.unregisterMBean(new ObjectName(_name));
      } catch (InstanceNotFoundException ignore) {
      } catch (Exception e) {
        throw new CacheException("unregister JMX bean, ObjectName: " + _name, e);
      }
    }
  }

  @Override
  public void managerCreated(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ManagerMXBeanImpl _mBean = new ManagerMXBeanImpl((CacheManagerImpl) m);
    String _name = managerName(m);
    try {
      mbs.registerMBean(_mBean, new ObjectName(_name));
    } catch (InstanceAlreadyExistsException e) {
    } catch (Exception e) {
      throw new CacheException("register JMX bean, ObjectName: " + _name, e);
    }
  }

  @Override
  public void managerDestroyed(CacheManager m) {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    String _name = managerName(m);
    try {
      try {
        mbs.unregisterMBean(new ObjectName(_name));
      } catch (InstanceNotFoundException ignore) {
      }
    } catch (Exception e) {
      throw new CacheException("Error unregister JMX bean, ObjectName: " + _name, e);
    }
  }

  private static String managerName(CacheManager cm) {
    return
      "org.cache2k" + ":" +
        "type=CacheManager" +
        ",name=" + sanitizeNameAsJmxValue(cm.getName());
  }

  private String standardName(CacheManager cm, Cache c) {
    return
      "org.cache2k" + ":" +
        "type=Cache" +
        ",manager=" + sanitizeNameAsJmxValue(cm.getName()) +
        ",name=" + sanitizeNameAsJmxValue(c.getName());
  }

  /**
   * Names can be used as JMX values directly, but if containing a comma we need
   * to do quoting.
   *
   * @see CacheManagerImpl#checkName(String)
   */
  private static String sanitizeNameAsJmxValue(String s) {
    if (s.indexOf(',') >= 0) {
      return '"' + s + '"';
    }
    return s;
  }

}
