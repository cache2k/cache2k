package org.cache2k.extra.jmx;

/*
 * #%L
 * cache2k JMX support
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

import org.cache2k.Cache;
import org.cache2k.CacheManager;
import org.cache2k.core.api.HealthInfoElement;
import org.cache2k.core.api.InternalCache;
import org.cache2k.jmx.CacheManagerMXBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
* @author Jens Wilke
*/
public class ManagerMXBeanImpl implements CacheManagerMXBean {

  private final CacheManager manager;

  public ManagerMXBeanImpl(CacheManager manager) {
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

  void sortHealthInfoList(List<HealthInfoElement> li) {
    Collections.sort(li, new Comparator<HealthInfoElement>() {
      @Override
      public int compare(HealthInfoElement o1, HealthInfoElement o2) {
        if (!o1.getLevel().equals(o2.getLevel())) {
          return (HealthInfoElement.FAILURE.equals(o1.getLevel())) ? -1 : 1;
        }
        return o1.getCache().getName().compareTo(o2.getCache().getName());
      }
    });
  }

  static String constructHealthString(List<HealthInfoElement> sortedList) {
    if (sortedList.isEmpty()) {
      return "ok";
    }
    boolean comma = false;
    StringBuilder sb = new StringBuilder();
    for (HealthInfoElement hi : sortedList) {
      if (comma) {
        sb.append("; ");
      }
      sb.append(hi.getLevel()).append(": ")
        .append("[").append(hi.getCache().getName() + "] ").append(hi.getMessage());
      comma = true;
    }
    return sb.toString();
  }

  @Override
  public void clear() {
    manager.clear();
  }

  @Override
  public String getVersion() { return CacheManager.PROVIDER.getVersion(); }

  @Override
  public String getBuildNumber() { return "not used"; }

}
