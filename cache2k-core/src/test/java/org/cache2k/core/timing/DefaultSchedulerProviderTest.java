package org.cache2k.core.timing;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.cache2k.core.CacheClosedException;
import org.cache2k.operation.Scheduler;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Checks that actual scheduler threads are stopped and restarted.
 *
 * @author Jens Wilke
 */
public class DefaultSchedulerProviderTest {

  @Test
  public void createAndClose() throws Exception {
    DefaultSchedulerProvider provider = new DefaultSchedulerProvider();
    Scheduler s1 = provider.supply(null);
    Scheduler s2 = provider.supply(null);
    s1.schedule(() -> { }, 123);
    s2.schedule(() -> { }, 123);
    ((AutoCloseable) s1).close();
    s1.schedule(() -> { }, 123);
    s2.schedule(() -> { }, 123);
    ((AutoCloseable) s2).close();
    assertThatCode(() -> {
        s1.schedule(() -> { }, 123);
      }
    ).isInstanceOf(CacheClosedException.class);
  }

  @Test
  public void doubleClose() throws Exception {
    DefaultSchedulerProvider provider = new DefaultSchedulerProvider();
    Scheduler s1 = provider.supply(null);
    s1.schedule(() -> { }, 123);
    ((AutoCloseable) s1).close();
    ((AutoCloseable) s1).close();
  }

  @Test
  public void closeAndCreate() throws Exception {
    DefaultSchedulerProvider provider = new DefaultSchedulerProvider();
    Scheduler s1 = provider.supply(null);
    s1.schedule(() -> { }, 123);
    ((AutoCloseable) s1).close();
    s1 = provider.supply(null);
    s1.schedule(() -> { }, 123);
  }

}
