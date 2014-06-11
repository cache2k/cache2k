package org.cache2k.impl.timer;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
 * @author Jens Wilke; created: 2014-04-27
 */
public final class NoPayloadTask extends BaseTimerTask {

  TimerListener listener;

  public NoPayloadTask(long time, TimerListener listener) {
    super(time);
    this.listener = listener;
  }

  @Override
  protected boolean fire(long now) {
    TimerListener l = listener;
    if (l != null) {
      l.fire(now);
      return true;
    }
    return false;
  }

  @Override
  public void cancel() {
    listener = null;
  }

  @Override
  public boolean isCancelled() {
    return listener == null;
  }

}
