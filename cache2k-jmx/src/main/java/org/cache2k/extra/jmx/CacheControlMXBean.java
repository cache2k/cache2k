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

import org.cache2k.operation.CacheOperation;
import org.cache2k.operation.CacheInfo;

import java.util.Date;

/**
 * Combined interface of basic cache information and cache control.
 * Mirrors the functions of {@link org.cache2k.operation.CacheControl}
 *
 * @author Jens Wilke
 */
public interface CacheControlMXBean {

  /**
   * @see CacheInfo#getName()
   */
  String getName();

  /**
   * @see CacheInfo#getManagerName()
   */
  String getManagerName();

  /**
   * @see CacheInfo#getKeyType()
   */
  String getKeyType();

  /**
   * @see CacheInfo#getValueType()
   */
  String getValueType();

  /**
   * @see CacheInfo#getSize()
   */
  long getSize();

  /**
   * @see CacheInfo#getEntryCapacity()
   */
  long getEntryCapacity();

  /**
   * @see CacheInfo#getMaximumWeight()
   */
  long getMaximumWeight();

  /**
   * @see CacheInfo#getTotalWeight()
   */
  long getTotalWeight();

  /**
   * @see CacheInfo#getCapacityLimit()
   */
  long getCapacityLimit();

  /**
   * @see CacheInfo#getImplementation()
   */
  String getImplementation();

  /**
   * @see CacheInfo#isLoaderPresent()
   */
  boolean isLoaderPresent();

  /**
   * @see CacheInfo#isWeigherPresent()
   */
  boolean isWeigherPresent();

  /**
   * @see CacheInfo#isStatisticsEnabled()
   */
  boolean isStatisticsEnabled();

  /**
   * @see CacheInfo#getCreatedTime()
   */
  Date getCreatedTime();

  /**
   * @see CacheInfo#getClearedTime()
   */
  Date getClearedTime();

  /**
   * @see CacheOperation#clear()
   */
  void clear();

  /**
   * @see CacheOperation#changeCapacity(long)()
   */
  void changeCapacity(long entryCountOrWeight);

}
