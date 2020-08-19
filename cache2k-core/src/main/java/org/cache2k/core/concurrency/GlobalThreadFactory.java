package org.cache2k.core.concurrency;

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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

/**
 * Factory which names the threads uniquely. If threads stop the previous numbers
 * will be reused.
 *
 * @author Jens Wilke
 */
public class GlobalThreadFactory implements ThreadFactory {

  private static final ConcurrentMap<String, String> NAMES_RUNNING =
    new ConcurrentHashMap<String, String>();

  private final String prefix;

  public GlobalThreadFactory(String threadNamePrefix) {
    prefix = threadNamePrefix;
  }

  protected String generateName(int id) {
    return prefix + '-' + id;
  }

  @Override
  public Thread newThread(final Runnable r) {
    String name;
    int id = 1;
    for (;;) {
      name = generateName(id);
      if (NAMES_RUNNING.putIfAbsent(name, name) == null) {
        break;
      }
      id++;
    }
    final String finalName = name;
    Runnable myRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          r.run();
        } finally {
          NAMES_RUNNING.remove(finalName);
        }
      }
    };
    Thread thr = new Thread(myRunnable);
    thr.setName(name);
    thr.setDaemon(true);
    return thr;
  }

}
