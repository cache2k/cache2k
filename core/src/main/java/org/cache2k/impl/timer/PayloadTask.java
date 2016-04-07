package org.cache2k.impl.timer;

/*
 * #%L
 * cache2k core package
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
* @author Jens Wilke; created: 2014-03-23
*/
public final class PayloadTask<T> extends BaseTimerTask {

  T payload;
  TimerPayloadListener<T> listener;

  public PayloadTask(long time, T payload, TimerPayloadListener<T> listener) {
    super(time);
    this.payload = payload;
    this.listener = listener;
  }

  protected boolean fire(long now) {
    final TimerPayloadListener<T> l = listener;
    final T pl = payload;
    if (l != null && pl != null) {
      l.fire(pl, time);
      return true;
    }
    return false;
  }

  /**
   * Cancel the timer execution.
   */
  public void cancel() {
    listener = null;
    payload = null;
  }

  public boolean isCancelled() {
    return listener == null;
  }

}
