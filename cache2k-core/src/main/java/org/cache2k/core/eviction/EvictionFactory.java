package org.cache2k.core.eviction;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.operation.Weigher;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.core.api.InternalCacheBuildContext;
import org.cache2k.core.HeapCache;
import org.cache2k.core.SegmentedEviction;

/**
 * @author Jens Wilke
 */
public class EvictionFactory {

  /**
   * Construct segmented or queued eviction. For the moment hard coded.
   * If capacity is at least 1000 we use 2 segments if 2 or more CPUs are available.
   * Segmenting the eviction only improves for lots of concurrent inserts or evictions,
   * there is no effect on read performance.
   */
  public Eviction constructEviction(InternalCacheBuildContext ctx,
                                    HeapCacheForEviction hc, InternalEvictionListener l,
                                    Cache2kConfig config, int availableProcessors) {
    boolean strictEviction = config.isStrictEviction();
    boolean boostConcurrency = config.isBoostConcurrency();
    long maximumWeight = config.getMaximumWeight();
    long entryCapacity = config.getEntryCapacity();
    Weigher weigher = null;
    if (config.getWeigher() != null) {
      weigher = (Weigher) ctx.createCustomization(config.getWeigher());
      if (maximumWeight <= 0) {
        throw new IllegalArgumentException(
          "maximumWeight > 0 expected. Weigher requires to set maximumWeight");
      }
      entryCapacity = -1;
    } else {
      if (entryCapacity < 0) {
        entryCapacity = Cache2kConfig.DEFAULT_ENTRY_CAPACITY;
      }
      if (entryCapacity == 0) {
        throw new IllegalArgumentException("entryCapacity of 0 is not supported.");
      }
    }
    int segmentCountOverride = HeapCache.TUNABLE.segmentCountOverride;
    int segmentCount =
      EvictionFactory.determineSegmentCount(
        strictEviction, availableProcessors,
        boostConcurrency, entryCapacity, maximumWeight, segmentCountOverride);
    Eviction[] segments = new Eviction[segmentCount];
    long maxSize = EvictionFactory.determineMaxSize(entryCapacity, segmentCount);
    long maxWeight = EvictionFactory.determineMaxWeight(maximumWeight, segmentCount);
    for (int i = 0; i < segments.length; i++) {
      segments[i]= new ClockProPlusEviction(hc, l, maxSize, weigher, maxWeight, strictEviction);
    }
    Eviction eviction = segmentCount == 1 ? segments[0] : new SegmentedEviction(segments);
    if (config.getIdleScanTime() != null) {
      IdleProcessing idleProcessing =
        new IdleProcessing(ctx.getTimeReference(), ctx.createScheduler(),
          eviction, config.getIdleScanTime().toMillis());
      eviction = new IdleProcessingEviction(eviction, idleProcessing);
    }
    return eviction;
  }

  public static long determineMaxSize(long entryCapacity, int segmentCount) {
    if (entryCapacity < 0) {
      return -1;
    }
    if (entryCapacity == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    }
    long maxSize = entryCapacity / segmentCount;
    if (entryCapacity % segmentCount > 0) {
      maxSize++;
    }
    return maxSize;
  }

  public static long determineMaxWeight(long maximumWeight, int segmentCount) {
    if (maximumWeight < 0) {
      return -1;
    }
    long maxWeight = maximumWeight / segmentCount;
    if (maximumWeight == Long.MAX_VALUE) {
      return Long.MAX_VALUE;
    } else if (maximumWeight % segmentCount > 0) {
      maxWeight++;
    }
    return maxWeight;
  }

  /**
   * Determine number of segments based on the the available processors.
   * For small cache sizes, no segmentation happens at all.
   */
  public static int determineSegmentCount(boolean strictEviction, int availableProcessors,
                                          boolean boostConcurrency, long entryCapacity,
                                          long maxWeight, int segmentCountOverride) {
    long segmentationCutoffCapacity = 1000;
    if (strictEviction) {
      return 1;
    }
    if (entryCapacity >= 0 && entryCapacity < segmentationCutoffCapacity) {
      return 1;
    }
    if (maxWeight >= 0 && maxWeight < segmentationCutoffCapacity) {
      return 1;
    }
    int segmentCount = 1;
    if (availableProcessors > 1) {
      segmentCount = 2;
      if (boostConcurrency) {
        segmentCount = 2 << (31 - Integer.numberOfLeadingZeros(availableProcessors));
      }
    }
    if (segmentCountOverride > 0) {
      segmentCount = 1 << (32 - Integer.numberOfLeadingZeros(segmentCountOverride - 1));
    } else {
      int maxSegments = availableProcessors * 2;
      segmentCount = Math.min(segmentCount, maxSegments);
    }
    return segmentCount;
  }

}
