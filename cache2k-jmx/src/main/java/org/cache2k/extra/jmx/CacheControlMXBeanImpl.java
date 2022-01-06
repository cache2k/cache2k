package org.cache2k.extra.jmx;

/*-
 * #%L
 * cache2k JMX support
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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
import org.cache2k.operation.CacheControl;

import java.time.Instant;
import java.util.Date;

/**
 * Use the implementation from core and decorate with the JMX bean
 * interface.
 *
 * @author Jens Wilke
 */
class CacheControlMXBeanImpl implements CacheControlMXBean {

  private final Cache<?, ?> cache;

  public CacheControlMXBeanImpl(Cache<?, ?> cache) {
    this.cache = cache;
  }

  public CacheControl getCacheControl() {
    return CacheControl.of(cache);
  }

  @Override
  public String getKeyType() {
    return getCacheControl().getKeyType().getTypeName();
  }

  @Override
  public String getValueType() {
    return getCacheControl().getValueType().getTypeName();
  }

  @Override
  public String getName() {
    return getCacheControl().getName();
  }

  @Override
  public String getManagerName() {
    return getCacheControl().getManagerName();
  }

  @Override
  public long getSize() {
    return getCacheControl().getSize();
  }

  @Override
  public long getEntryCapacity() {
    return getCacheControl().getEntryCapacity();
  }

  @Override
  public long getCapacityLimit() {
    return getCacheControl().getCapacityLimit();
  }

  @Override
  public long getMaximumWeight() {
    return getCacheControl().getMaximumWeight();
  }

  @Override
  public long getTotalWeight() {
    return getCacheControl().getTotalWeight();
  }

  @Override
  public Date getCreatedTime() {
    return Date.from(getCacheControl().getCreatedTime());
  }

  @Override
  public Date getClearedTime() {
    Instant v = getCacheControl().getClearedTime();
    return v != null ? Date.from(v) : null;
  }

  @Override
  public String getImplementation() {
    return getCacheControl().getImplementation();
  }

  @Override
  public boolean isLoaderPresent() {
    return getCacheControl().isLoaderPresent();
  }

  @Override
  public boolean isWeigherPresent() {
    return getCacheControl().isWeigherPresent();
  }

  @Override
  public boolean isStatisticsEnabled() {
    return getCacheControl().isStatisticsEnabled();
  }

  @Override
  public void clear() {
    getCacheControl().clear();
  }

  @Override
  public void changeCapacity(long entryCountOrWeight) {
    getCacheControl().changeCapacity(entryCountOrWeight);
  }

}
