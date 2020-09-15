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

import org.cache2k.core.CacheClosedException;
import org.cache2k.core.Entry;
import org.cache2k.core.InternalCache;

/**
 * @author Jens Wilke
 */
abstract class Tasks<K, V> extends SimpleTimerTask {
  private Entry<K, V> entry;
  private InternalCache<K, V> cache;

  Tasks<K, V> to(InternalCache<K, V> c, Entry<K, V> e) {
    cache = c;
    entry = e;
    return this;
  }

  /**
   * Null out references to avoid mem leaks, when timer is cancelled.
   */
  @Override
  public boolean cancel() {
    if (super.cancel()) {
      cache = null;
      entry = null;
      return true;
    }
    return false;
  }

  protected InternalCache<K, V> getCache() {
    return cache;
  }

  protected Entry<K, V> getEntry() {
    return entry;
  }

  public abstract void fire() throws Exception;

  public final void run() {
    try {
      fire();
    } catch (CacheClosedException ignore) {
    } catch (Throwable ex) {
      cache.logAndCountInternalException("Timer execution exception", ex);
    }
  }

  static class RefreshTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getCache().timerEventRefresh(getEntry(), this);
    }
  }

  static class ExpireTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getCache().timerEventExpireEntry(getEntry(), this);
    }
  }

  static class RefreshExpireTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getCache().timerEventProbationTerminated(getEntry(), this);
    }
  }

}
