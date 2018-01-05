package org.cache2k.core.concurrency;

/*
 * #%L
 * cache2k core
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

/**
 * Factory which names the threads uniquely. If threads stop the previous numbers
 * will be reused.
 *
 * @author Jens Wilke; created: 2014-06-10
 */
public class GlobalThreadFactory implements ThreadFactory {

  final static ConcurrentMap<String,String> NAMES_RUNNING =
    new ConcurrentHashMap<String, String>();

  String prefix;

  public GlobalThreadFactory(String _threadNamePrefix) {
    if (_threadNamePrefix == null) {
      throw new NullPointerException("Missing thread name prefix");
    }
    prefix = _threadNamePrefix;
  }

  protected String generateName(int id) {
    return prefix + '-' + id;
  }

  @Override
  public Thread newThread(final Runnable r) {
    String _name;
    int id = 1;
    for (;;) {
      _name = generateName(id);
      if (NAMES_RUNNING.putIfAbsent(_name, _name) == null) {
        break;
      }
      id++;
    }
    final String _finalName = _name;
    final Runnable _myRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          r.run();
        } finally {
          NAMES_RUNNING.remove(_finalName);
        }
      }
    };
    Thread thr = new Thread(_myRunnable);
    thr.setName(_name);
    thr.setDaemon(true);
    return thr;
  }

}
