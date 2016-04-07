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
import org.cache2k.impl.CacheManagerImpl;
import org.cache2k.impl.InternalCache;
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
    return v;
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
