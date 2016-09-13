package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k ee
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
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import static org.junit.Assert.*;

import org.cache2k.core.util.Log;
import org.cache2k.junit.FastTests;
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
import java.util.Optional;

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
    assertEquals("OK", _health);
    m.close();
  }

  private MBeanInfo getCacheManagerInfo(String _name) throws Exception {
    ObjectName on = getCacheManagerObjectName(_name);
    return server.getMBeanInfo(on);
  }

  private ObjectName getCacheManagerObjectName(final String _name) throws MalformedObjectNameException {
    return new ObjectName("org.cache2k:type=CacheManager,name=" + _name);
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

  private void checkAttribute(String _name, Object _expected) throws Exception {
    Object v = retrieve(_name);
    assertEquals("Value of attribute '" + _name + "'", _expected, v);
  }

  private Object retrieve(final String _name) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
    return server.getAttribute(objectName, _name);
  }

  private MBeanInfo getCacheInfo(String _name) throws Exception {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ObjectName on = constructCacheObjectName(_name);
    return mbs.getMBeanInfo(on);
  }

  private ObjectName constructCacheObjectName(final String _name) throws MalformedObjectNameException {
    return new ObjectName("org.cache2k:type=Cache,manager=default,name=" + _name);
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

  @Test
  public void cacheNameDisambiguation() {
    final String _uniqueName =
      this.getClass().getName() + ".cacheNameDisambiguation";
    Log.SuppressionCounter sc = new Log.SuppressionCounter();
    Log.registerSuppression(CacheManager.class.getName() + ":" + _uniqueName, sc);
    CacheManager cm = CacheManager.getInstance(_uniqueName);
    Cache c0 = Cache2kBuilder.forUnknownTypes()
      .manager(cm)
      .eternal(true)
      .name(_uniqueName)
      .build();
    Cache c1 = Cache2kBuilder.forUnknownTypes()
      .manager(cm)
      .eternal(true)
      .name(_uniqueName)
      .build();
    assertEquals("org.cache2k.ee.impl.JmxSupportTest.cacheNameDisambiguation~1", c1.getName());
    c0.close();
    c1.close();
    assertEquals(2, sc.getWarnCount());
  }

}
