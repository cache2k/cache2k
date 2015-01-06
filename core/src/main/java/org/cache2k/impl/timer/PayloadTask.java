package org.cache2k.impl.timer;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
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
