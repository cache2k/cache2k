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
 * Generic interface of a timer service.
 *
 * @author Jens Wilke; created: 2014-03-23
 */
public abstract class TimerService {

  /**
   * Add a timer that fires at the specified time.
   */
  public abstract <T> CancelHandle add(TimerListener _listener, long _fireTime);

  public abstract <T> CancelHandle add(TimerPayloadListener<T> _listener, T _payload, long _fireTime);

  /**
   * Return the tasks in the timer queue including the cancelled.
   */
  public abstract int getQueueSize();
  public abstract long getEventsDelivered();
  public abstract long getEventsScheduled();
  public abstract long getPurgeCount();
  public abstract long getCancelCount();
  public abstract long getFireExceptionCount();

  public interface CancelHandle {

    /**
     * Cancel the timer execution. This is a fast and unsynchronized method.
     */
    public void cancel();

    public boolean isCancelled();

  }

}
