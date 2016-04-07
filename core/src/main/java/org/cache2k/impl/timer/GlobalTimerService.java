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
 * @author Jens Wilke; created: 2014-03-22
 */
public class GlobalTimerService extends TimerService {

  private static TimerService queue;

  public static TimerService getInstance() {
    if (queue  == null) {
      queue = new GlobalTimerService(null, Runtime.getRuntime().availableProcessors() * 2);
    }
    return queue;
  }

  int racyRoundRobinCounter = 0;
  ArrayHeapTimerQueue[] timerQueues;

  public GlobalTimerService(String _managerName, int _threadCount) {
    String _separator = "-";
    if (_managerName != null) {
      _separator = ":" + _managerName + ":";
    }
    timerQueues = new ArrayHeapTimerQueue[_threadCount];
    for (int i = 0; i < timerQueues.length; i++) {
      timerQueues[i] =
        new ArrayHeapTimerQueue("cache2k" + _separator + "timer-" + i);
    }
  }

  public <T> CancelHandle add(TimerPayloadListener<T> l, T _payload, long t) {
    racyRoundRobinCounter = (racyRoundRobinCounter + 1) % timerQueues.length;
    return timerQueues[racyRoundRobinCounter].add(l, _payload, t);
  }

  public CancelHandle add(TimerListener l, long t) {
    racyRoundRobinCounter = (racyRoundRobinCounter + 1) % timerQueues.length;
    return timerQueues[racyRoundRobinCounter].add(l, t);
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

  @Override
  public long getCancelCount() {
    long v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getCancelCount();
    }
    return v;
  }

  @Override
  public long getFireExceptionCount() {
    long v = 0;
    for (int i = 0; i < timerQueues.length; i++) {
      v += timerQueues[i].getFireExceptionCount();
    }
    return v;
  }

}
