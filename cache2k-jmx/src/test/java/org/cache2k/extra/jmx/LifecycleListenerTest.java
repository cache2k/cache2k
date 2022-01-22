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
import org.cache2k.CacheException;
import org.junit.jupiter.api.Test;

import javax.management.MalformedObjectNameException;

import java.lang.management.ManagementFactory;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for corner cases
 *
 * @author Jens Wilke
 */
public class LifecycleListenerTest {

  @Test
  public void ignoreDoubleCreateEvent() {
    LifecycleListener listener = LifecycleListener.SUPPLIER.supply(null);
    Cache cache = Cache2kBuilder.forUnknownTypes().build();
    listener.onCacheCreated(cache, null);
    listener.onCacheCreated(cache, null);
  }

  @Test
  public void unregisterUnknown() {
    LifecycleListener.unregisterBean(ManagementFactory.getPlatformMBeanServer(),
      "org.cache2:type=Cache,name=xyz");
  }

  @Test
  public void rethrowExceptions() {
    assertThatCode(() -> LifecycleListener.registerBean(null, null, null))
      .isInstanceOf(CacheException.class);
    assertThatCode(() -> LifecycleListener.unregisterBean(null, null))
      .isInstanceOf(CacheException.class);
  }

  @Test
  public void malformedName() {
    assertThatCode(() -> LifecycleListener.objectName("!@#$%^&*"))
      .getRootCause().isInstanceOf(MalformedObjectNameException.class);
  }

}
