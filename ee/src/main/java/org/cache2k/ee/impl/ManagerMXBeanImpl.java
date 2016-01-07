package org.cache2k.ee.impl;

/*
 * #%L
 * cache2k for enterprise environments
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import org.cache2k.Cache;
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.InternalCache;
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
      if (c instanceof InternalCache) {
        v = Math.max(v, ((InternalCache) c).getInfo().getHealth());
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
