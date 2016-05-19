package org.cache2k.core;

/*
 * #%L
 * cache2k core
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

/**
 * @author Jens Wilke
 */
public interface CommonMetrics {

  long getPutNewEntryCount();
  long getPutHitCount();
  long getCasOperationCount();
  long getPutNoReadHitCount();

  /**
   * Correction counter for read usage
   */
  long getContainsButHitCount();

  /**
   * A valid entry was removed from the cache.
   */
  long getRemoveCount();

  long getHeapHitButNoReadCount();

  /**
   * Count of timer events delivered to this cache.
   */
  long getTimerEventCount();

  /**
   * The cache produced an exception by itself that should have been prevented.
   */
  long getInternalExceptionCount();

  /**
   * Entry was loaded, triggered by a get()
   */
  long getLoadCount();

  /**
   * Entry was loaded again, triggered by a get()
   */
  long getReloadCount();

  /**
   * Entry was loaded again, triggered by timer
   */
  long getRefreshCount();

  /**
   * Accumulated milliseconds spend in load operations.
   */
  long getLoadMillis();

  interface Updater extends CommonMetrics {

    void putNewEntry();
    void putNewEntry(long cnt);

    void putHit();
    void putHit(long cnt);

    void putNoReadHit();
    void putNoReadHit(long cnt);

    void casOperation();
    void casOperation(long cnt);

    void containsButHit();
    void containsButHit(long cnt);

    void remove();
    void remove(long cnt);

    void heapHitButNoRead();
    void heapHitButNoRead(long cnt);

    void timerEvent();
    void timerEvent(long cnt);

    void internalException();

    void load(long _millis);
    void load(long cnt, long _millis);

    void reload(long _millis);
    void reload(long cnt, long _millis);

    void refresh(long _millis);
    void refresh(long cnt, long _millis);

  }

}
