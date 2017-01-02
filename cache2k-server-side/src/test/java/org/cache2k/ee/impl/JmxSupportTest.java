package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k server side
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import static org.junit.Assert.*;

import org.cache2k.core.CacheManagerImpl;
import org.cache2k.core.util.Log;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Simple test to check that the support and the management object appear
 * and disappear.
 *
 * @author Jens Wilke; created: 2014-10-10
 */
@Category(FastTests.class)
public class JmxSupportTest {

  private static final MBeanServerConnection server =  ManagementFactory.getPlatformMBeanServer();

  private ObjectName objectName;

  @Test
  public void testManagerPresent() throws Exception {
    String _name = getClass().getName() + ".testManagerPresent";
    CacheManager m = CacheManager.getInstance(_name);
    MBeanInfo i = getCacheManagerInfo(_name);
    assertEquals(ManagerMXBeanImpl.class.getName(), i.getClassName());
    m.close();
  }

  @Test
  public void emptyCacheManager_healthOkay() throws Exception {
    String _name = getClass().getName() + ".emptyCacheManager_healthOkay";
    CacheManager m = CacheManager.getInstance(_name);
    MBeanInfo i = getCacheManagerInfo(_name);
    assertEquals(ManagerMXBeanImpl.class.getName(), i.getClassName());
    String _health = (String) server.getAttribute(getCacheManagerObjectName(_name), "HealthStatus");
    assertEquals("ok", _health);
    m.close();
  }

  /**
   * Construct three caches with multiple issues and check the health string of the manager JMX.
   */
  @Test
  public void multipleWarnings() throws Exception {
    final String _MANAGER_NAME = getClass().getName() + ".multipleWarnings";
    final String _CACHE_NAME_BAD_HASHING = "cacheWithBadHashing";
    final String _CACHE_NAME_KEY_MUTATION = "cacheWithKeyMutation";
    final String _CACHE_NAME_MULTIPLE_ISSUES = "cacheWithMultipleIssues";
    final String _SUPPRESS1 =  "org.cache2k.Cache/" + _MANAGER_NAME + ":" + _CACHE_NAME_KEY_MUTATION;
    final String _SUPPRESS2 =  "org.cache2k.Cache/" + _MANAGER_NAME + ":" + _CACHE_NAME_MULTIPLE_ISSUES;
    Log.registerSuppression(_SUPPRESS1, new Log.SuppressionCounter());
    Log.registerSuppression(_SUPPRESS2, new Log.SuppressionCounter());
    CacheManager m = CacheManager.getInstance(_MANAGER_NAME);
    Cache _cacheWithBadHashing = Cache2kBuilder.of(Object.class, Object.class)
      .manager(m)
      .name(_CACHE_NAME_BAD_HASHING)
      .eternal(true)
      .build();
    Cache _cacheWithKeyMutation = Cache2kBuilder.of(Object.class, Object.class)
      .manager(m)
      .name(_CACHE_NAME_KEY_MUTATION)
      .eternal(true)
      .entryCapacity(50)
      .build();
    Cache _cacheWithMultipleIssues = Cache2kBuilder.of(Object.class, Object.class)
      .manager(m)
      .name(_CACHE_NAME_MULTIPLE_ISSUES)
      .entryCapacity(50)
      .eternal(true)
      .build();
    for (int i = 0; i < 9; i++) {
      _cacheWithBadHashing.put(new KeyForMutation(), 1);
    }
    for (int i = 0; i < 100; i++) {
      _cacheWithMultipleIssues.put(new KeyForMutation(), 1);
    }
    for (int i = 0; i < 100; i++) {
      KeyForMutation v = new KeyForMutation();
      _cacheWithKeyMutation.put(v, 1);
      _cacheWithMultipleIssues.put(v, 1);
      v.value = 1;
    }
    String _health = (String) server.getAttribute(getCacheManagerObjectName(_MANAGER_NAME), "HealthStatus");
    assertEquals(
      "FAILURE: [cacheWithKeyMutation] hash quality is 0 (threshold: 5); " +
      "FAILURE: [cacheWithMultipleIssues] hash quality is 0 (threshold: 5); " +
      "WARNING: [cacheWithBadHashing] hash quality is 7 (threshold: 20); " +
      "WARNING: [cacheWithKeyMutation] key mutation detected; " +
      "WARNING: [cacheWithMultipleIssues] key mutation detected", _health);
    Log.deregisterSuppression(_SUPPRESS1);
    Log.deregisterSuppression(_SUPPRESS2);
    m.close();
  }

  private static class KeyForMutation {
    int value = 0;
    public int hashCode() { return value; }
  }

  static MBeanInfo getCacheManagerInfo(String _name) throws Exception {
    ObjectName on = getCacheManagerObjectName(_name);
    return server.getMBeanInfo(on);
  }

  private static ObjectName getCacheManagerObjectName(final String _name) throws MalformedObjectNameException {
    if (needsQuoting(_name)) {
      return new ObjectName("org.cache2k:type=CacheManager,name=\"" + _name + "\"");
    } else {
      return new ObjectName("org.cache2k:type=CacheManager,name=" + _name);
    }
  }

  private static boolean needsQuoting(String _name) {
    return _name.contains(",");
  }

  @Test(expected = InstanceNotFoundException.class)
  public void testManagerDestroyed() throws Exception {
    String _name = getClass().getName() + ".testManagerDestroyed";
    CacheManager m = CacheManager.getInstance(_name);
    MBeanInfo i = getCacheManagerInfo(_name);
    assertEquals(ManagerMXBeanImpl.class.getName(), i.getClassName());
    m.close();
    getCacheManagerInfo(_name);
  }

  @Test
  public void managerClear_noCache() throws Exception {
    String _name = getClass().getName() + ".managerClear_noCache";
    CacheManager m = CacheManager.getInstance(_name);
    server.invoke(getCacheManagerObjectName(_name), "clear", new Object[0], new String[0]);
    m.close();
  }

  @Test
  public void managerAttributes() throws Exception {
    String _name = getClass().getName() + ".managerAttributes";
    CacheManager m = CacheManager.getInstance(_name);
    objectName = getCacheManagerObjectName(_name);
    checkAttribute("Version", ((CacheManagerImpl) m).getVersion());
    checkAttribute("BuildNumber", ((CacheManagerImpl) m).getBuildNumber());
    m.close();
  }

  @Test
  public void testCacheCreated() throws Exception {
    String _name = getClass().getName() + ".testCacheCreated";
    Cache c = Cache2kBuilder.of(Object.class, Object.class)
      .name(_name)
      .eternal(true)
      .build();
    MBeanInfo i = getCacheInfo(_name);
    assertEquals(CacheMXBeanImpl.class.getName(), i.getClassName());
    c.close();
  }

  @Test
  public void testInitialProperties() throws Exception {
    Date _beforeCreateion = new Date();
    String _name = getClass().getName() + ".testInitialProperties";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>> () {}
      .name(_name)
      .eternal(true)
      .build();
    objectName = constructCacheObjectName(_name);
    checkAttribute("KeyType", "Long");
    checkAttribute("ValueType", "java.util.List<java.util.Collection<Long>>");
    checkAttribute("Size", 0L);
    checkAttribute("EntryCapacity", 2000L);
    checkAttribute("InsertCount", 0L);
    checkAttribute("MissCount", 0L);
    checkAttribute("RefreshCount", 0L);
    checkAttribute("RefreshFailedCount", 0L);
    checkAttribute("RefreshedHitCount", 0L);
    checkAttribute("ExpiredCount", 0L);
    checkAttribute("EvictedCount", 0L);
    checkAttribute("PutCount", 0L);
    checkAttribute("RemoveCount", 0L);
    checkAttribute("ClearedEntriesCount", 0L);
    checkAttribute("ClearCount", 0L);
    checkAttribute("KeyMutationCount", 0L);
    checkAttribute("LoadExceptionCount", 0L);
    checkAttribute("SuppressedLoadExceptionCount", 0L);
    checkAttribute("HitRate", 0.0);
    checkAttribute("HashQuality", 100);
    checkAttribute("MillisPerLoad", 0.0);
    checkAttribute("TotalLoadMillis", 0L);
    checkAttribute("Implementation", "HeapCache");
    checkAttribute("ClearedTime", null);
    checkAttribute("Alert", 0);
    assertTrue("reasonable CreatedTime", ((Date) retrieve("CreatedTime")).compareTo(_beforeCreateion) >= 0);
    assertTrue("reasonable InfoCreatedTime", ((Date) retrieve("InfoCreatedTime")).compareTo(_beforeCreateion) >= 0);
    assertTrue("reasonable InfoCreatedDeltaMillis", ((Integer) retrieve("InfoCreatedDeltaMillis")) >= 0);
    assertTrue("reasonable EvictionStatistics", retrieve("EvictionStatistics").toString().contains("impl="));
    assertTrue("reasonable IntegrityDescriptor", retrieve("IntegrityDescriptor").toString().startsWith("0."));
    c.close();
  }

  @Test
  public void testDisabled() throws Exception {
    String _name = getClass().getName() + ".testInitialProperties";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>> () {}
      .name(_name)
      .disableStatistics(true)
      .eternal(true)
      .build();
    objectName = constructCacheObjectName(_name);
    try {
      retrieve("Alert");
      fail("exception expected");
    } catch (InstanceNotFoundException ex) {
    }
    c.close();
  }

  private void checkAttribute(String _name, Object _expected) throws Exception {
    Object v = retrieve(_name);
    assertEquals("Value of attribute '" + _name + "'", _expected, v);
  }

  private Object retrieve(final String _name) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
    return server.getAttribute(objectName, _name);
  }

  static MBeanInfo getCacheInfo(String _name) throws Exception {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ObjectName on = constructCacheObjectName(_name);
    return mbs.getMBeanInfo(on);
  }

  static ObjectName constructCacheObjectName(final String _name) throws MalformedObjectNameException {
    if (needsQuoting(_name)) {
      return new ObjectName("org.cache2k:type=Cache,manager=default,name=\"" + _name + "\"");
    } else {
      return new ObjectName("org.cache2k:type=Cache,manager=default,name=" + _name);
    }
  }

  @Test(expected = InstanceNotFoundException.class)
  public void testCacheDestroyed() throws Exception {
    String _name = getClass().getName() + ".testCacheDestroyed";
    Cache c = Cache2kBuilder.of(Object.class, Object.class)
      .name(_name)
      .eternal(true)
      .build();
    MBeanInfo i = getCacheInfo(_name);
    assertEquals(CacheMXBeanImpl.class.getName(), i.getClassName());
    c.close();
    getCacheInfo(_name);
  }

}
