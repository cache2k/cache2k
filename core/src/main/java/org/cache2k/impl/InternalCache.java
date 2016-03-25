package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
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
import org.cache2k.CacheEntry;
import org.cache2k.LoadCompletedListener;
import org.cache2k.impl.util.Log;

import java.util.concurrent.Future;

/**
 * Interface to extended cache functions for the internal components.
 *
 * @author Jens Wilke
 */
public interface InternalCache<K, V> extends Cache<K, V>, CanCheckIntegrity {

  /** used from the cache manager */
  Log getLog();

  String getName();

  StorageAdapter getStorage();

  Class<?> getKeyType();

  Class<?> getValueType();

  void clearTimingStatistics();

  /** used from the cache manager for shutdown */
  Future<Void> cancelTimerJobs();

  void timerEventRefresh(Entry<K, V> e);

  void timerEventExpireEntry(Entry<K, V> e);

  InternalCacheInfo getInfo();

  InternalCacheInfo getLatestInfo();

  /**
   * Used by JCache impl, since access needs to trigger the TTI maybe use EP instead?
   */
  CacheEntry<K, V> replaceOrGet(K key, V _oldValue, V _newValue, CacheEntry<K, V> _dummyEntry);

  String getEntryState(K key);

}
