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
 * Generic interface of a timer service.
 *
 * @author Jens Wilke; created: 2014-03-23
 */
public abstract class TimerTaskQueue {

  public abstract <T> TimerTask<T> addTimer(TimerListener<T> l, T _payload, long _fireTime);

  /**
   * Schedule a cleanup of the timers internal data structures.
   */
  public abstract void schedulePurge();

  /**
   * Return the tasks in the timer queue including the cancelled.
   */
  public abstract int getQueueSize();
  public abstract long getEventsDelivered();
  public abstract long getEventsScheduled();
  public abstract long getPurgeCount();

}
