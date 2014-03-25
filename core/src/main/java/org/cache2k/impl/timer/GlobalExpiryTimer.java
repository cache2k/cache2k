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
 * @author Jens Wilke; created: 2014-03-22
 */
public class GlobalExpiryTimer extends TimerTaskQueue {

  int racyRoundRobinCounter = 0;
  ArrayHeapTimerQueue[] timerQueues;

  public GlobalExpiryTimer(String _managerName, int _threadCount) {
    timerQueues = new ArrayHeapTimerQueue[_threadCount];
    for (int i = 0; i < timerQueues.length; i++) {
      timerQueues[i] =
        new ArrayHeapTimerQueue("cache2k-timer-" + _managerName + "-" + i);
    }
  }

  public <T> TimerTask<T> addTimer(TimerListener<T> l, T _payload, long t) {
    racyRoundRobinCounter = (racyRoundRobinCounter + 1) % timerQueues.length;
    return timerQueues[racyRoundRobinCounter].addTimer(l, _payload, t);
  }

  @Override
  public void schedulePurge() {
    racyRoundRobinCounter = (racyRoundRobinCounter + 1) % timerQueues.length;
    timerQueues[racyRoundRobinCounter].schedulePurge();
  }

  @Override
  public int getQueueSize() {
    int v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getQueueSize();
    }
    return v;
  }

  @Override
  public long getEventsDelivered() {
    long v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getEventsDelivered();
    }
    return v;
  }

  @Override
  public long getEventsScheduled() {
    long v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getEventsScheduled();
    }
    return v;
  }

  @Override
  public long getPurgeCount() {
    long v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getPurgeCount();
    }
    return v;
  }

}
