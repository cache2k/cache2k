package org.cache2k.core;

/*-
 * #%L
 * cache2k core implementation
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

import org.assertj.core.api.Assertions;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.core.api.InternalConfig;
import org.cache2k.core.concurrency.ThreadFactoryProvider;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Explicit tests for internal config options
 *
 * @author Jens Wilke
 */
public class InternalConfigTest {

  @Test
  public void evictionSegmentCount() {
    Cache<Object, Object> cache =
      Cache2kBuilder.forUnknownTypes()
        .with(InternalConfig.class, b ->
            b.evictionSegmentCount(32))
        .build();
    assertThat(cache.toString()).contains("eviction31=");
    cache.close();
  }

  @Test
  public void threadFactoryProvider() {
    AtomicBoolean executed = new AtomicBoolean();
    Cache<Object, Object> cache =
      Cache2kBuilder.forUnknownTypes()
        .loader(key -> key)
        .with(InternalConfig.class, b ->
          b.threadFactoryProvider(namePrefix -> {
            executed.set(true);
            return ThreadFactoryProvider.DEFAULT.newThreadFactory(namePrefix);
          }))
        .build();
    cache.loadAll(Arrays.asList(1, 2, 3));
    assertThat(executed.get()).isTrue();
    cache.close();
  }

}
