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

import org.cache2k.core.api.InternalCache;
import org.cache2k.core.api.InternalCacheInfo;
import org.cache2k.core.common.AbstractCacheStatistics;

/**
 * Provide updating statistics.
 *
 * @author Jens Wilke
 */
public class CacheStatisticsMXBeanImpl
  extends AbstractCacheStatistics implements CacheStatisticsMXBean {

  private InternalCache cache;

  public CacheStatisticsMXBeanImpl(InternalCache cache) {
    this.cache = cache;
  }

  /** Request new info all the */
  @Override
  protected InternalCacheInfo info() {
    return cache.getInfo();
  }

}
