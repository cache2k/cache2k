package org.cache2k.extra.jmx;

/*-
 * #%L
 * cache2k JMX support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.config.CacheBuildContext;
import org.cache2k.config.CustomizationReferenceSupplier;
import org.cache2k.core.log.Log;
import org.cache2k.event.CacheClosedListener;
import org.cache2k.event.CacheCreatedListener;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.CompletableFuture;

/**
 * Register/unregister MX beans with the cache lifecycle.
 *
 * @author Jens Wilke
 */
class LifecycleListener implements CacheCreatedListener, CacheClosedListener {

  static final Log LOG = Log.getLog(JmxSupport.class);
  static final CustomizationReferenceSupplier<LifecycleListener> SUPPLIER =
    new CustomizationReferenceSupplier<>(new LifecycleListener());

  /** Singleton */
  private LifecycleListener() { }

  @Override
  public <K, V> CompletableFuture<Void> onCacheCreated(Cache<K, V> cache,
                                                       CacheBuildContext<K, V> ctx) {
    MBeanServer mbs = getPlatformMBeanServer();
    CacheControlMXBean management = new CacheControlMXBeanImpl(cache);
    registerBean(mbs, management, createCacheControlName(cache.getCacheManager(), cache));
    if (!management.isStatisticsEnabled()) {
      return CompletableFuture.completedFuture(null);
    }
    registerBean(mbs,
      new CacheStatisticsMXBeanImpl(cache),
      createCacheStatisticsName(cache.getCacheManager(), cache));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<Void> onCacheClosed(Cache<?, ?> cache) {
    MBeanServer mbs = getPlatformMBeanServer();
    unregisterBean(mbs, createCacheControlName(cache.getCacheManager(), cache));
    unregisterBean(mbs, createCacheStatisticsName(cache.getCacheManager(), cache));
    return CompletableFuture.completedFuture(null);
  }

  static void registerBean(MBeanServer mbs, Object obj, String name) {
    try {
      mbs.registerMBean(obj, objectName(name));
    } catch (InstanceAlreadyExistsException existing) {
      LOG.debug("register failure, name: " + name, existing);
    } catch (Exception e) {
      throw new CacheException("register JMX bean, ObjectName: " + name, e);
    }
  }

  /**
   * Unregister rethrow unexpected exceptions.
   */
  static void unregisterBean(MBeanServer mbs, String name) {
    try {
      mbs.unregisterMBean(objectName(name));
    } catch (InstanceNotFoundException ignore) {
    } catch (Exception e) {
      throw new CacheException("unregister JMX bean, ObjectName: " + name, e);
    }
  }

  /**
   * Malformed names should never happen since we sanitize the names
   */
  static ObjectName objectName(String name) {
    try {
      return new ObjectName(name);
    } catch (MalformedObjectNameException e) {
      throw new CacheException("Error created ObjectName, name: " + name, e);
    }
  }

  private static MBeanServer getPlatformMBeanServer() {
    return ManagementFactory.getPlatformMBeanServer();
  }

  private static String createCacheControlName(CacheManager cm, Cache<?, ?> c) {
    return
      "org.cache2k" + ":" +
        "type=Cache" +
        ",manager=" + sanitizeNameAsJmxValue(cm.getName()) +
        ",name=" + sanitizeNameAsJmxValue(c.getName());
  }

  private static String createCacheStatisticsName(CacheManager cm, Cache<?, ?> c) {
    return
      "org.cache2k" + ":" +
        "type=CacheStatistics" +
        ",manager=" + sanitizeNameAsJmxValue(cm.getName()) +
        ",name=" + sanitizeNameAsJmxValue(c.getName());
  }

  /**
   * Names can be used as JMX values directly, but if containing a comma we need
   * to do quoting.
   *
   * See {@code org.cache2k.core.CacheManagerImpl#checkName(String)}
   */
  private static String sanitizeNameAsJmxValue(String s) {
    if (s.indexOf(',') >= 0) {
      return '"' + s + '"';
    }
    return s;
  }

}
