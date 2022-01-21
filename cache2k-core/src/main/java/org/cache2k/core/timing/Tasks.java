package org.cache2k.core.timing;

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

import org.cache2k.CacheClosedException;
import org.cache2k.core.Entry;

/**
 * @author Jens Wilke
 */
abstract class Tasks<K, V> extends TimerTask {

  private Entry<K, V> entry;
  private TimerEventListener<K, V> target;

  Tasks<K, V> to(TimerEventListener<K, V> target, Entry<K, V> e) {
    this.target = target;
    entry = e;
    return this;
  }

  /**
   * Null out references to avoid mem leaks, when timer is cancelled.
   * Only null if cancel is successful, since execution might go on
   * in parallel.
   */
  @Override
  protected boolean cancel() {
    if (super.cancel()) {
      target = null;
      entry = null;
      return true;
    }
    return false;
  }

  protected TimerEventListener<K, V> getTarget() {
    return target;
  }

  protected Entry<K, V> getEntry() {
    return entry;
  }

  public abstract void fire();

  protected final void action() {
    try {
      fire();
    } catch (CacheClosedException ignore) {
    } catch (Throwable t) {
      throw new RuntimeException("Exception in event processing," +
        " for cache: " + getTarget().getName(), t);
    }
  }

  static class RefreshTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getTarget().timerEventRefresh(getEntry(), this);
    }
  }

  static class ExpireTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getTarget().timerEventExpireEntry(getEntry(), this);
    }
  }

  static class RefreshExpireTimerTask<K, V> extends Tasks<K, V> {
    public void fire() {
      getTarget().timerEventProbationTerminated(getEntry(), this);
    }
  }

}
