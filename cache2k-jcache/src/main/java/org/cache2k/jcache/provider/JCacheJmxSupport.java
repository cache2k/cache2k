package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.annotation.Nullable;
import org.cache2k.event.CacheClosedListener;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("WeakerAccess")
public class JCacheJmxSupport implements CacheClosedListener {

  private static final MBeanServer PLATFORM_SERVER = ManagementFactory.getPlatformMBeanServer();
  public static final JCacheJmxSupport SINGLETON = new JCacheJmxSupport();

  private JCacheJmxSupport() { }

  @Override
  public @Nullable CompletableFuture<Void> onCacheClosed(Cache cache) {
    disableStatistics(cache);
    disableJmx(cache);
    return null;
  }

  public void enableStatistics(JCacheAdapter c) {
    MBeanServer mbs = PLATFORM_SERVER;
    String name = createStatisticsObjectName(c.cache);
    try {
       mbs.registerMBean(
         new JCacheJmxStatisticsMXBean(c),
         new ObjectName(name));
    } catch (Exception e) {
      throw new IllegalStateException("Error registering JMX bean, name='" + name + "'", e);
    }
  }

  public void disableStatistics(Cache c) {
    MBeanServer mbs = PLATFORM_SERVER;
    String name = createStatisticsObjectName(c);
    try {
      mbs.unregisterMBean(new ObjectName(name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new IllegalStateException("Error unregister JMX bean, name='" + name + "'", e);
    }
  }

  public void enableJmx(Cache c, javax.cache.Cache ca) {
    MBeanServer mbs = PLATFORM_SERVER;
    String name = createJmxObjectName(c);
    try {
       mbs.registerMBean(new JCacheJmxCacheMXBean(ca), new ObjectName(name));
    } catch (Exception e) {
      throw new IllegalStateException("Error register JMX bean, name='" + name + "'", e);
    }
  }

  public void disableJmx(Cache c) {
    MBeanServer mbs = PLATFORM_SERVER;
    String name = createJmxObjectName(c);
    try {
      mbs.unregisterMBean(new ObjectName(name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new IllegalStateException("Error unregister JMX bean, name='" + name + "'", e);
    }
  }

  public String createStatisticsObjectName(Cache cache) {
    return "javax.cache:type=CacheStatistics," +
          "CacheManager=" + sanitizeName(cache.getCacheManager().getName()) +
          ",Cache=" + sanitizeName(cache.getName());
  }

  public String createJmxObjectName(Cache cache) {
    return "javax.cache:type=CacheConfiguration," +
          "CacheManager=" + sanitizeName(cache.getCacheManager().getName()) +
          ",Cache=" + sanitizeName(cache.getName());
  }

  /**
   * Filter illegal chars, same rule as in TCK or RI?
   */
  public static String sanitizeName(String string) {
    return string == null ? "" : string.replaceAll(":|=|\n|,", ".");
  }

}
