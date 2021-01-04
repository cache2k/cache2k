package org.cache2k.extra.jmx;

/*
 * #%L
 * cache2k JMX support
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

import org.cache2k.core.common.BaseCacheControl;
import org.cache2k.operation.CacheStatistics;

import java.time.Instant;
import java.util.Date;

/**
 * Use the implementation from core and decorate with the JMX bean
 * interface.
 *
 * @author Jens Wilke
 */
public class CacheControlMXBeanImpl implements CacheControlMXBean {

  private BaseCacheControl cacheControl;

  public CacheControlMXBeanImpl(BaseCacheControl cacheControl) {
    this.cacheControl = cacheControl;
  }

  @Override
  public String getKeyType() {
    return cacheControl.getKeyType().getTypeName();
  }

  @Override
  public String getValueType() {
    return cacheControl.getValueType().getTypeName();
  }

  @Override
  public String getName() {
    return cacheControl.getName();
  }

  @Override
  public String getManagerName() {
    return cacheControl.getManagerName();
  }

  @Override
  public long getSize() {
    return cacheControl.getSize();
  }

  @Override
  public long getEntryCapacity() {
    return cacheControl.getEntryCapacity();
  }

  @Override
  public long getCapacityLimit() {
    return cacheControl.getCapacityLimit();
  }

  @Override
  public long getMaximumWeight() {
    return cacheControl.getMaximumWeight();
  }

  @Override
  public long getTotalWeight() {
    return cacheControl.getTotalWeight();
  }

  @Override
  public Date getCreatedTime() {
    return Date.from(cacheControl.getCreatedTime());
  }

  @Override
  public Date getClearedTime() {
    Instant v = cacheControl.getClearedTime();
    return v != null ? Date.from(v) : null;
  }

  @Override
  public String getImplementation() {
    return cacheControl.getImplementation();
  }

  @Override
  public boolean isLoaderPresent() {
    return cacheControl.isLoaderPresent();
  }

  @Override
  public boolean isWeigherPresent() {
    return cacheControl.isWeigherPresent();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return cacheControl.isStatisticsEnabled();
  }

  @Override
  public void clear() {
    cacheControl.clear();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    cacheControl.changeCapacity(entryCountOrWeight);
  }

}
