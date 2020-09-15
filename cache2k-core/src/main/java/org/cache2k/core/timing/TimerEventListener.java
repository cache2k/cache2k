package org.cache2k.core.timing;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.core.Entry;
import org.cache2k.core.InternalCache;

/**
 * Notifications from the {@link Timing} to the {@link InternalCache} upon timer
 * events. This interface is part of {@link InternalCache} at the moment, but maybe better
 * separated in the future.
 *
 * @author Jens Wilke
 */
public interface TimerEventListener<K, V> {

  /**
   * Called by the timer when an entry is expired or before actual expiry
   * when the entry needs to switch into sharp expiry mode. The actual action
   * to be performed is detected by checking the {@link Entry#getNextRefreshTime()}
   *
   * @param task timer task as returned by {@link Entry#getTask} to check whether
   *             the timer task is still valid after we obtained the entry lock
   */
  void timerEventExpireEntry(Entry<K, V> e, Object task);

  /**
   *
   *
   * @param e see {@link #timerEventExpireEntry(Entry, Object)}
   * @param task see {@link #timerEventExpireEntry(Entry, Object)}
   */
  void timerEventRefresh(Entry<K, V> e, Object task);

  /**
   *
   * @param e see {@link #timerEventExpireEntry(Entry, Object)}
   * @param task see {@link #timerEventExpireEntry(Entry, Object)}
   */
  void timerEventProbationTerminated(Entry<K, V> e, Object task);

}
