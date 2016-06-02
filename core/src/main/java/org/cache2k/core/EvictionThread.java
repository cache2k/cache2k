package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

import java.util.concurrent.ThreadFactory;

/**
 * @author Jens Wilke
 */
public class EvictionThread {

  private final static ThreadFactory THREAD_FACTORY =
    HeapCache.TUNABLE.threadFactoryProvider.newThreadFactory("cache2k-evict");
  private final static int YIELD_SPINS = 33;
  private final static int YIELD_TIMEOUT = 5432;

  private final Object lock = new Object();
  private Thread thread;
  private Job[] jobs = new Job[0];
  private volatile boolean stopped = true;

  public void ensureRunning() {
    if (stopped) {
      synchronized (lock) {
        if (!stopped) { return; }
        Runnable r = new Runnable() {
          @Override
          public void run() {
            runJobs();
          }
        };
        thread = THREAD_FACTORY.newThread(r);
        thread.start();
        stopped = false;
      }
    }
  }

  public void addJob(Job j) {
    synchronized (lock) {
      Job[] ja = new Job[jobs.length + 1];
      System.arraycopy(jobs, 0, ja, 0, jobs.length);
      ja[jobs.length] = j;
      jobs = ja;
    }
  }

  /**
   * Remove the job from the list. After the method returns it is guaranteed that
   * the job will not be executed by pool.
   */
  public void removeJob(Job j) {
    synchronized (lock) {
      Job[] ja = new Job[jobs.length - 1];
      int idx = 0;
      try {
        for (Job j2 : jobs) {
          if (j != j2) {
            ja[idx++] = j2;
          }
        }
      } catch (ArrayIndexOutOfBoundsException ex) {
        return;
      }
      jobs = ja;
    }
  }

  private void runJobs() {
    boolean _hadWork = false;
    long t0 = System.currentTimeMillis();
    int _spinCounter = YIELD_SPINS;
    for (;;) {
      synchronized (lock) {
        for (Job j : jobs) {
          _hadWork |= j.runEvictionJob();
        }
      }
      if (_hadWork) {
        _spinCounter = YIELD_SPINS;
        continue;
      }
      if (_spinCounter-- >= 0) {
        try {
          Thread.sleep(27);
        } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
        }
        continue;
      }
      if (t0 == 0) {
        t0 = System.currentTimeMillis() + YIELD_TIMEOUT;
        _spinCounter = YIELD_SPINS;
        continue;
      }
      if (System.currentTimeMillis() < t0) {
        _spinCounter = YIELD_SPINS;
        continue;
      }
      break;
    }
    stopped = true;
  }

  interface Job {
    boolean runEvictionJob();
  }

}
