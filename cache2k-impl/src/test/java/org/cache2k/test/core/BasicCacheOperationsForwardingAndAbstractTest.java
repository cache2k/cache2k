package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.AbstractCache;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.ForwardingCache;
import org.cache2k.testing.category.FastTests;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

/**
 * Reuse the basic operations test {@link ForwardingCache}
 *
 * @see BasicCacheOperationsTest
 */
@Category(FastTests.class)
public class BasicCacheOperationsForwardingAndAbstractTest extends BasicCacheOperationsTest {

  static Cache<Integer, Integer> staticCache;
  @BeforeClass
  public static void setUp() {
    final Cache<Integer, Integer>  c = Cache2kBuilder
            .of(Integer.class, Integer.class)
            .name(BasicCacheOperationsForwardingAndAbstractTest.class)
            .retryInterval(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
            .eternal(true)
            .entryCapacity(1000)
            .permitNullValues(true)
            .build();
    final Cache<Integer, Integer> _forwardingCache = new ForwardingCache<Integer, Integer>() {
      @Override
      protected Cache<Integer, Integer> delegate() {
        return c;
      }
    };
    final Cache<Integer, Integer> _abstractCache = new AbstractCache<Integer, Integer>();

    InvocationHandler h = new InvocationHandler() {
      @Override
      public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
        try {
          method.invoke(_abstractCache, args);
          fail("exception expected");
        } catch(InvocationTargetException ex) {
          assertEquals("expected exception",
            UnsupportedOperationException.class, ex.getTargetException().getClass());
        }
        try {
          return method.invoke(_forwardingCache, args);
        } catch(InvocationTargetException ex) {
          throw ex.getTargetException();
        }
      }
    };
    staticCache = (Cache<Integer, Integer>)
      Proxy.newProxyInstance(
        BasicCacheOperationsForwardingAndAbstractTest.class.getClassLoader(),
        new Class<?>[]{Cache.class}, h);
  }

  @Before
  public void initCache() {
    cache = staticCache;
    statistics().reset();
  }

  @After
  public void cleanupCache() {
    cache.clear();
  }

  @AfterClass
  public static void tearDown() {
    staticCache.clearAndClose();
    staticCache.close();
  }

}
