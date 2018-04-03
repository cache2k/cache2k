package org.cache2k.test.osgi;

/*
 * #%L
 * cache2k tests for OSGi
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
import org.cache2k.CacheEntry;
import org.cache2k.CacheManager;
import org.cache2k.configuration.Cache2kConfiguration;
import org.cache2k.event.CacheEntryCreatedListener;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test the OSGi enabled bundle. Tests are run via the failsafe maven plugin and not with
 * surefire, since these are integration tests. This is critical since we must run
 * after the package phase for the the bundle package to exist.
 *
 * @author Jens Wilke
 */
@org.junit.runner.RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class OsgiIT {

  @Configuration
  public Option[] config() {
    String _userDir = System.getProperty("user.dir");
    String _ownPath = "/test-osgi";
    String _workspaceDir = _userDir;
    if (_workspaceDir.endsWith(_ownPath)) {
      _workspaceDir = _workspaceDir.substring(0,_workspaceDir.length() -  _ownPath.length());
    }
    return options(
      bundle("file:///" + _workspaceDir + "/cache2k-all/target/cache2k-all-" + System.getProperty("cache2k.version") + ".jar"),
      /*
      try to use api and impl directly
      https://github.com/cache2k/cache2k/issues/83
      bundle("file:///" + _workspaceDir + "/cache2k-api/target/cache2k-api-" + System.getProperty("cache2k.version") + ".jar"),
      bundle("file:///" + _workspaceDir + "/cache2k-impl/target/cache2k-impl-" + System.getProperty("cache2k.version") + ".jar"),
      */
      junitBundles()
    );
  }

  @Test
  public void testSimple() {
    CacheManager m = CacheManager.getInstance("testSimple");
    Cache<String, String> c =
      Cache2kBuilder.of(String.class, String.class)
        .manager(m)
        .eternal(true)
        .build();
    c.put("abc", "123");
    assertTrue(c.containsKey("abc"));
    assertEquals("123", c.peek("abc"));
    c.close();
  }

  @Test
  public void testWithAnonBuilder() {
    CacheManager m = CacheManager.getInstance("testWithAnonBuilder");
    Cache<String, String> c =
      new Cache2kBuilder<String, String>() {}
        .manager(m)
        .eternal(true)
        .build();
    c.put("abc", "123");
    assertTrue(c.containsKey("abc"));
    assertEquals("123", c.peek("abc"));
    c.close();
  }

  /**
   * Simple test to see whether configuration package is exported.
   */
  @Test
  public void testConfigurationPackage() {
    Cache2kConfiguration<String, String> c =
      Cache2kBuilder.of(String.class, String.class)
        .eternal(true)
        .toConfiguration();
    assertTrue(c.isEternal());
  }

  /**
   * Simple test to see whether event package is exported.
   */
  @Test
  public void testEventPackage() {
    CacheManager m = CacheManager.getInstance("testEventPackage");
    final AtomicInteger _count = new AtomicInteger();
    Cache<String, String> c =
      new Cache2kBuilder<String, String>() {}
        .manager(m)
        .eternal(true)
        .addListener(new CacheEntryCreatedListener<String, String>() {
          @Override
          public void onEntryCreated(final Cache<String, String> cache, final CacheEntry<String, String> entry) {
            _count.incrementAndGet();
          }
        })
        .build();
    c.put("abc", "123");
    assertTrue(c.containsKey("abc"));
    assertEquals("123", c.peek("abc"));
    assertEquals(1, _count.get());
    c.close();
  }

  @Test
  public void testDefaultCacheManager() {
    assertEquals("default", CacheManager.getInstance().getName());
  }

  @Test
  public void testConfigFileUsedDifferentManager() {
    assertEquals("specialDefaultName", CacheManager.getInstance(OsgiIT.class.getClassLoader()).getName());
  }

}
