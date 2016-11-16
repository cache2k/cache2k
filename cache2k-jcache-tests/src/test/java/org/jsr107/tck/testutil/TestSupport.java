/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.testutil;

import javax.cache.Cache;
import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Unit test support base class
 *
 * @author Yannis Cosmadopoulos
 * @author Greg Luck
 * @since 1.0
 */
public class TestSupport {

  /**
   * The logger
   */
  protected static final Logger LOG = Logger.getLogger(TestSupport.class.getName());

  private final Map<Class<?>, Class<?>> unwrapClasses = Collections.synchronizedMap(new HashMap<Class<?>, Class<?>>());
  private Properties unwrapProperties;

  /**
   * Looks up the given attributeName for the given cache.
   *
   * @throws javax.cache.CacheException - all exceptions are wrapped in CacheException
   */
  public static Object lookupManagementAttribute(Cache cache, MBeanType type, String attributeName) throws Exception {

    MBeanServer mBeanServer = TestSupport.resolveMBeanServer();

    ObjectName objectName = calculateObjectName(cache, type);
    Object attribute = mBeanServer.getAttribute(objectName, attributeName);
    return attribute;
  }

  /**
   * Creates an object name using the scheme
   * "javax.cache:type=Cache&lt;Statistics|Configuration&gt;,CacheManager=&lt;cacheManagerName&gt;,name=&lt;cacheName&gt;"
   */
  public static ObjectName calculateObjectName(Cache cache, MBeanType type) {
    try {
      ObjectName name = new ObjectName("javax.cache:type=" + type + "," +
          "CacheManager=" + mbeanSafe(cache.getCacheManager().getURI().toString()) +
          ",Cache=" + mbeanSafe(cache.getName()));
      return name;
    } catch (MalformedObjectNameException e) {
      throw new CacheException(e);
    }
  }

  /**
   * Filter out invalid ObjectName characters from string.
   *
   * @param string input string
   * @return A valid JMX ObjectName attribute value.
   */
  public static String mbeanSafe(String string) {
    return string == null ? "" : string.replaceAll(":|=|\n|,", ".");
  }

  /**
   * To test your implementation specify system properties per the following RI
   * examples:
   * -Djavax.management.builder.initial=org.jsr107.ri.management.RITCKMBeanServerBuilder
   * -Dorg.jsr107.tck.management.agentId=RIMBeanServer
   */
  public static MBeanServer resolveMBeanServer() {

    if (System.getProperty("javax.management.builder.initial") == null) {
      throw new RuntimeException("You must set the 'javax.management.builder" +
          ".initial' property so that the TCK can find your MBeanServer'");
    }
    if (System.getProperty("org.jsr107.tck.management.agentId") == null) {
      throw new RuntimeException("You must set the 'org.jsr107.tck.management.agentId'" +
          "system property so that the TCK can find your MBeanServer agent by " +
          "its ID.'");
    }

    String agentId = System.getProperty("org.jsr107.tck.management.agentId");
    ArrayList<MBeanServer> mBeanServers = MBeanServerFactory.findMBeanServer(agentId);
    if (mBeanServers.size() < 1) {
      throw new CacheException("The specification requires registration of " +
          "MBeans in an implementation specific MBeanServer. A search for an " +
          "MBeanServer did not find any.");
    }
    return mBeanServers.get(0);
  }

  protected CacheManager getCacheManager() {
    return Caching.getCachingProvider().getCacheManager();
  }

  protected String getTestCacheName() {
    return getClass().getName();
  }

  protected Class<?> getUnwrapClass(Class<?> unwrappableClass) {


    //contains check since null values are allowed
    if (this.unwrapClasses.containsKey(unwrappableClass)) {
      return this.unwrapClasses.get(unwrappableClass);
    }

    //cannot specify $ in Maven so replace with '.'
    final String unwrapClassName = System.getProperty(unwrappableClass.getName().replace('$', '.'));
    if (unwrapClassName == null || unwrapClassName.trim().length() == 0) {
      this.unwrapClasses.put(unwrappableClass, null);
      return null;
    }

    try {
      final Class<?> unwrapClass = Class.forName(unwrapClassName);
      this.unwrapClasses.put(unwrappableClass, unwrapClass);
      return unwrapClass;
    } catch (ClassNotFoundException e) {
      LOG.warning("Failed to load unwrap class " + unwrapClassName + " for unwrappable class: " + unwrappableClass);
      this.unwrapClasses.put(unwrappableClass, null);
      return null;
    }

  }

  public static enum MBeanType {

    CacheConfiguration,

    CacheStatistics

  }
}
