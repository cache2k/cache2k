package org.cache2k.core.timing;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke
 */
public class DefaultScheduler implements Scheduler {

  public static final Scheduler INSTANCE = new DefaultScheduler();

  private ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(
    1, new DaemonThreadFactory());

  private DefaultScheduler() { }

  @Override
  public void schedule(Runnable runnable, long millis) {
    long delay = millis - System.currentTimeMillis();
    delay = Math.max(0, delay);
    executor.schedule(runnable, delay, TimeUnit.MILLISECONDS);
  }

  static final class DaemonThreadFactory implements ThreadFactory {
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r);
      t.setDaemon(true);
      t.setName("cache2k-scheduler");
      t.setPriority(Thread.MAX_PRIORITY);
      return t;
    }
  }

}
