package org.cache2k.impl.serverSide;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.Cache;
import org.cache2k.core.CacheManagerImpl;
import org.cache2k.core.HealthInfoElement;
import org.cache2k.core.InternalCache;
import org.cache2k.jmx.CacheManagerMXBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author Jens Wilke; created: 2014-10-09
*/
public class ManagerMXBeanImpl implements CacheManagerMXBean {

  private final CacheManagerImpl manager;

  public ManagerMXBeanImpl(CacheManagerImpl manager) {
    this.manager = manager;
  }

  @Override
  public String getHealthStatus() {
    List<HealthInfoElement> li = new ArrayList<HealthInfoElement>();
    for (Cache c : manager.getActiveCaches()) {
      InternalCache ic = (InternalCache) c;
      li.addAll(ic.getInfo().getHealth());
    }
    sortHealthInfoList(li);
    return constructHealthString(li);
  }

  void sortHealthInfoList(final List<HealthInfoElement> li) {
    Collections.sort(li, new Comparator<HealthInfoElement>() {
      @Override
      public int compare(final HealthInfoElement o1, final HealthInfoElement o2) {
        if (!o1.getLevel().equals(o2.getLevel())) {
          return (HealthInfoElement.FAILURE.equals(o1.getLevel())) ? -1 : 1;
        }
        return o1.getCache().getName().compareTo(o2.getCache().getName());
      }
    });
  }

  static String constructHealthString(final List<HealthInfoElement> _sortedList) {
    if (_sortedList.isEmpty()) {
      return "ok";
    }
    boolean _comma = false;
    StringBuilder sb = new StringBuilder();
    for (HealthInfoElement hi : _sortedList) {
      if (_comma) {
        sb.append("; ");
      }
      sb.append(hi.getLevel()).append(": ")
        .append("[").append(hi.getCache().getName() + "] ").append(hi.getMessage());
      _comma = true;
    }
    return sb.toString();
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
