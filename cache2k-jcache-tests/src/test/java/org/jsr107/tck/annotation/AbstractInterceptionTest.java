/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.jsr107.tck.annotation;

import org.jsr107.tck.testutil.AbstractTestExcluder;
import org.junit.Rule;
import org.junit.rules.MethodRule;

import javax.cache.annotation.BeanProvider;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Base class that ALL annotation/interceptor tests MUST extend from
 *
 * @author Eric Dalquist
 * @version $Revision$
 */
public class AbstractInterceptionTest {
  private static final BeanProvider beanProvider;

  static {
    BeanProvider localBeanProvider = null;
    try {
      final ServiceLoader<BeanProvider> serviceLoader = ServiceLoader.load(BeanProvider.class);
      final Iterator<BeanProvider> it = serviceLoader.iterator();
      localBeanProvider = it.hasNext() ? it.next() : null;
    } catch (Throwable t) {
      //ignore
      System.err.println("Failed to load BeanProvider SPI impl, annotation tests will be ignored");
      t.printStackTrace(System.err);
    }

    beanProvider = localBeanProvider;
  }


  /**
   * Rule used to exclude tests that do not implement Annotations
   */
  @Rule
  public final MethodRule rule = new AbstractTestExcluder() {
    @Override
    protected boolean isExcluded(String methodName) {
      //Exclude all tests if annotations are not supported or no beanProvider has been set
      return beanProvider == null;
    }
  };

  /**
   * Loads a specified bean by type, used to retrieve an annotated bean to test from the underlying implementation
   *
   * @param beanClass The type to load
   * @return The loaded bean
   */
  protected final <T> T getBeanByType(Class<T> beanClass) {
    if (beanProvider == null) {
      throw new IllegalStateException("No tests should be run if beanProvider is null");
    }

    return beanProvider.getBeanByType(beanClass);
  }
}