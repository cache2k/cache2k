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
import org.cache2k.Cache2kBuilder;

import org.cache2k.operation.Weigher;
import org.cache2k.annotation.Nullable;
import org.cache2k.testing.category.FastTests;
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.of;

/**
 * Simple test to check that the support and the management object appear
 * and disappear.
 *
 * @author Jens Wilke
 */
public class JmxSupportTest {

  private static final MBeanServerConnection SERVER =  ManagementFactory.getPlatformMBeanServer();

  private @Nullable ObjectName objectName;


  private static String maybeQuote(String name) {
    if (needsQuoting(name)) {
      return "\"" + name + "\"";
    }
    return name;
  }

  private static boolean needsQuoting(String name) {
    return name.contains(",");
  }

  @Test
  public void testCacheCreated() throws Exception {
    String name = getClass().getName() + ".testCacheCreated";
    Cache c = of(Object.class, Object.class)
      .name(name)
      .setup(JmxSupport::enable)
      .eternal(true)
      .build();
    MBeanInfo i = getCacheInfo(name);
    assertThat(i).isNotNull();
    c.close();
    try {
      i = getCacheInfo(name);
      fail("exception expected");
    } catch (InstanceNotFoundException expected) { }
  }

  @Test
  public void testCacheCreated_notFound() throws Exception {
    assertThatCode(() -> {
      String name = getClass().getName() + ".testCacheCreated";
      Cache c = Cache2kBuilder.of(Object.class, Object.class)
        .name(name)
        .eternal(true)
        .build();
      MBeanInfo i = getCacheInfo(name);
    }).isInstanceOf(InstanceNotFoundException.class);
  }

  @Test
  public void testInitialProperties() throws Exception {
    Date beforeCreation = new Date();
    String name = getClass().getName() + ".testInitialProperties";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>>() {
    }
      .name(name)
      .eternal(true)
      .setup(JmxSupport::enable)
      .strictEviction(true)
      .build();
    objectName = constructCacheObjectName(name);
    checkAttribute("Name", name);
    checkAttribute("ManagerName", c.getCacheManager().getName());
    checkAttribute("KeyType", "Long");
    checkAttribute("ValueType", "java.util.List<java.util.Collection<Long>>");
    checkAttribute("Size", 0L);
    checkAttribute("EntryCapacity", 1802L);
    checkAttribute("CapacityLimit", 1802L);
    checkAttribute("MaximumWeight", -1L);
    checkAttribute("TotalWeight", 0L);
    checkAttribute("Implementation", "HeapCache");
    checkAttribute("ClearedTime", null);
    checkAttribute("LoaderPresent", false);
    checkAttribute("WeigherPresent", false);
    assertThat(((Date) retrieve("CreatedTime")).compareTo(beforeCreation) >= 0)
      .as("reasonable CreatedTime")
      .isTrue();
    objectName = constructCacheStatisticsObjectName(name);
    checkAttribute("InsertCount", 0L);
    checkAttribute("MissCount", 0L);
    checkAttribute("RefreshCount", 0L);
    checkAttribute("RefreshFailedCount", 0L);
    checkAttribute("RefreshedHitCount", 0L);
    checkAttribute("ExpiredCount", 0L);
    checkAttribute("EvictedCount", 0L);
    checkAttribute("PutCount", 0L);
    checkAttribute("RemoveCount", 0L);
    checkAttribute("ClearedCount", 0L);
    checkAttribute("ClearCallsCount", 0L);
    checkAttribute("KeyMutationCount", 0L);
    checkAttribute("LoadExceptionCount", 0L);
    checkAttribute("SuppressedLoadExceptionCount", 0L);
    checkAttribute("HitRate", 0.0);
    checkAttribute("MillisPerLoad", 0.0);
    checkAttribute("TotalLoadMillis", 0L);
    objectName = constructCacheObjectName(name);
    invoke("clear");
    assertThat(((Date) retrieve("ClearedTime")).compareTo(beforeCreation) >= 0)
      .as("reasonable ClearedTime")
      .isTrue();
    invoke("changeCapacity", 4711, "long");
    checkAttribute("EntryCapacity", 4711L);
    checkAttribute("CapacityLimit", 4711L);
    c.close();
  }

  @Test
  public void testDisabledStatistics() throws Exception {
    String name = getClass().getName() + ".testDisabledStatistics";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>>() { }
      .name(name)
      .disableStatistics(true)
      .setup(JmxSupport::enable)
      .build();
    objectName = constructCacheObjectName(name);
    checkAttribute("KeyType", "Long");
    objectName = constructCacheStatisticsObjectName(name);
    assertThatCode(() -> retrieve("HitRate"))
      .isInstanceOf(InstanceNotFoundException.class);
    c.clear();
  }

  @Test
  public void testDisableMonitoring() throws Exception {
    String name = getClass().getName() + ".testDisabledMonitoring";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>>() { }
      .name(name)
      .setup(JmxSupport::enable)
      .disableMonitoring(true)
      .build();
    objectName = constructCacheObjectName(name);
    assertThatCode(() -> retrieve("KeyType"))
      .isInstanceOf(InstanceNotFoundException.class);
    objectName = constructCacheStatisticsObjectName(name);
    assertThatCode(() -> retrieve("HitRate"))
      .isInstanceOf(InstanceNotFoundException.class);
    c.clear();
  }

  @Test
  public void testWeigherWithSegmentation() throws Exception {
    String name = getClass().getName() + ".testWeigherWithSegmentation";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>>() { }
      .name(name)
      .eternal(true)
      .disableStatistics(false)
      .setup(JmxSupport::enable)
      .maximumWeight(123456789L)
      .weigher(new Weigher<Long, List<Collection<Long>>>() {
        @Override
        public int weigh(Long key, List<Collection<Long>> value) {
          return 1;
        }
      })
      .build();
    objectName = constructCacheObjectName(name);
    checkAttribute("KeyType", "Long");
    checkAttribute("ValueType", "java.util.List<java.util.Collection<Long>>");
    checkAttribute("Size", 0L);
    checkAttribute("EntryCapacity", -1L);
    checkAttribute("WeigherPresent", true);
    long v = (Long) retrieve("MaximumWeight");
    assertThat(v >= 123456789L).isTrue();
    checkAttribute("TotalWeight", 0L);
    objectName = constructCacheStatisticsObjectName(name);
    checkAttribute("EvictedOrRemovedWeight", 0L);
    c.close();
  }

  @Test
  public void testDisabled() throws Exception {
    String name = getClass().getName() + ".testInitialProperties";
    Cache c = new Cache2kBuilder<Long, List<Collection<Long>>>() { }
      .name(name)
      .disableStatistics(true)
      .eternal(true)
      .build();
    objectName = constructCacheObjectName(name);
    try {
      retrieve("Alert");
      fail("exception expected");
    } catch (InstanceNotFoundException ex) {
    }
    c.close();
  }

  private void checkAttribute(String name, @Nullable Object expected) throws Exception {
    Object v = retrieve(name);
    assertThat(v)
      .as("Value of attribute '" + name + "'")
      .isEqualTo(expected);
  }

  private Object retrieve(String name)
    throws MBeanException, AttributeNotFoundException, InstanceNotFoundException,
    ReflectionException, IOException {
    return SERVER.getAttribute(objectName, name);
  }

  private Object invoke(String methodName)
    throws ReflectionException, InstanceNotFoundException, MBeanException, IOException {
    return SERVER.invoke(objectName, methodName, null, null);
  }

  private Object invoke(String methodName, Object params, String signature)
    throws ReflectionException, InstanceNotFoundException, MBeanException, IOException {
    return SERVER.invoke(objectName, methodName, new Object[]{params}, new String[]{signature});
  }

  static MBeanInfo getCacheInfo(String name) throws Exception {
    MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
    ObjectName on = constructCacheObjectName(name);
    return mbs.getMBeanInfo(on);
  }

  static ObjectName constructCacheObjectName(String name) throws MalformedObjectNameException {
    return new ObjectName("org.cache2k:type=Cache,manager=default,name=" + maybeQuote(name));
  }

  static ObjectName constructCacheStatisticsObjectName(String name)
    throws MalformedObjectNameException {
    return new ObjectName("org.cache2k:type=CacheStatistics,manager=default," +
      "name=" + maybeQuote(name));
  }

  @Test
  public void testCacheDestroyed() {
    assertThatCode(() -> {
      String name = getClass().getName() + ".testCacheDestroyed";
      Cache c = Cache2kBuilder.of(Object.class, Object.class)
        .name(name)
        .eternal(true)
        .setup(JmxSupport::enable)
        .build();
      MBeanInfo i = getCacheInfo(name);
      c.close();
      getCacheInfo(name);
    }).isInstanceOf(InstanceNotFoundException.class);
  }

  @Test
  public void testEnableDisableJmxSupport() {
    assertThatCode(() -> {
      String name = getClass().getName() + ".testEnableDisable";
      Cache c = Cache2kBuilder.of(Object.class, Object.class)
        .name(name)
        .eternal(true)
        .setup(JmxSupport::enable)
        .setup(JmxSupport::disable)
        .build();
      MBeanInfo i = getCacheInfo(name);
      c.close();
      getCacheInfo(name);
    }).isInstanceOf(InstanceNotFoundException.class);
  }

}
