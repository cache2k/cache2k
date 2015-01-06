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
