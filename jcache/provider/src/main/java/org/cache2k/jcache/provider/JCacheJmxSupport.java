package org.cache2k.jcache.provider;

/*
 * #%L
 * cache2k JCache JSR107 implementation
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
import org.cache2k.impl.CacheUsageExcpetion;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;

/**
 * @author Jens Wilke; created: 2015-04-29
 */
public class JCacheJmxSupport implements CacheLifeCycleListener {

  private static final MBeanServer mBeanServer = MBeanServerFactory.createMBeanServer();

  /**
   * Register JMX objects. Called by manager if statistics support is requested.
   */
  public void registerCache(Cache c) {
  }

  @Override
  public void cacheCreated(CacheManager cm, Cache c) {
  }

  @Override
  public void cacheDestroyed(CacheManager cm, Cache c) {
    disableStatistics(c);
    disableConfiguration(c);
  }

  public void enableStatistics(JCacheAdapter c) {
    MBeanServer mbs = mBeanServer;
    String _name = createStatisticsObjectName(c.cache);
    try {
       mbs.registerMBean(
         new CacheJmxStatistics(c),
         new ObjectName(_name));
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
    }
  }

  public void disableStatistics(Cache c) {
    MBeanServer mbs = mBeanServer;
    String _name = createStatisticsObjectName(c);
    try {
      mbs.unregisterMBean(new ObjectName(_name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
    }
  }

  public void enableConfiguration(Cache c, javax.cache.Cache ca) {
    MBeanServer mbs = mBeanServer;
    String _name = createConfigurationObjectName(c);
    try {
       mbs.registerMBean(new CacheJmxConfiguration(ca), new ObjectName(_name));
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error registering JMX bean, name='" + _name + "'", e);
    }
  }

  public void disableConfiguration(Cache c) {
    MBeanServer mbs = mBeanServer;
    String _name = createConfigurationObjectName(c);
    try {
      mbs.unregisterMBean(new ObjectName(_name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new CacheUsageExcpetion("Error deregistering JMX bean, name='" + _name + "'", e);
    }
  }

  public String createStatisticsObjectName(Cache cache) {
    return "javax.cache:type=CacheStatistics," +
          "CacheManager=" + sanitizeName(cache.getCacheManager().getName()) +
          ",Cache=" + sanitizeName(cache.getName());
  }

  public String createConfigurationObjectName(Cache cache) {
    return "javax.cache:type=CacheConfiguration," +
          "CacheManager=" + sanitizeName(cache.getCacheManager().getName()) +
          ",Cache=" + sanitizeName(cache.getName());
  }

  /**
   * Filter illegal chars, same rule as in TCK.
   */
  public static String sanitizeName(String string) {
    return string == null ? "" : string.replaceAll(":|=|\n|,", ".");
  }

}
