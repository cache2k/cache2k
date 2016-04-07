package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
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

import org.cache2k.Cache;
import org.cache2k.impl.BaseCache;
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.threading.GlobalPooledExecutor;
import org.cache2k.jmx.CacheManagerMXBean;

/**
* @author Jens Wilke; created: 2014-10-09
*/
public class ManagerMXBeanImpl implements CacheManagerMXBean {

  CacheManagerImpl manager;

  public ManagerMXBeanImpl(CacheManagerImpl manager) {
    this.manager = manager;
  }

  @Override
  public int getAlert() {
    int v = 0;
    for (Cache c : manager) {
      if (c instanceof BaseCache) {
        v = Math.max(v, ((BaseCache) c).getInfo().getHealth());
      }
    }
    GlobalPooledExecutor ex = manager.getThreadPoolEventually();
    if (ex != null && ex.wasWarningLimitReached()) {
      v = Math.max(v, 1);
    }
    return v;
  }

  @Override
  public int getThreadsInPool() {
    GlobalPooledExecutor ex = manager.getThreadPoolEventually();
    if (ex != null) {
      return ex.getThreadInUseCount();
    }
    return 0;
  }

  @Override
  public int getPeakThreadsInPool() {
    GlobalPooledExecutor ex = manager.getThreadPoolEventually();
    if (ex != null) {
      return ex.getPeakThreadCount();
    }
    return 0;
  }

  @Override
  public void clear() {
    manager.clear();
  }

  @Override
  public String getVersion() { return manager.getVersion(); }

  @Override
  public String getBuildNumber() { return manager.getBuildNumber(); }

}
