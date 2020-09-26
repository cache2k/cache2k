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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * Task for the simple timer
 *
 * @author Jens Wilke
 */
public abstract class TimerTask implements Runnable {

  /**
   * The state of this task, chosen from the constants below.
   */
  private volatile int state = VIRGIN;

  static final AtomicIntegerFieldUpdater<TimerTask> STATE_UPDATER =
    AtomicIntegerFieldUpdater.newUpdater(TimerTask.class, "state");

  /**
   * This task has not yet been scheduled.
   */
  static final int VIRGIN = 0;

  /**
   * This task is scheduled for execution.  If it is a non-repeating task,
   * it has not yet been executed.
   */
  static final int SCHEDULED   = 1;

  /**
   * This non-repeating task has already executed (or is currently
   * executing) and has not been cancelled.
   */
  static final int EXECUTED    = 2;

  /**
   * This task has been cancelled (with a call to TimerTask.cancel).
   */
  static final int CANCELLED   = 3;

  /**
   * Execution time for this task.
   */
  volatile long executionTime;

  /**
   * Creates a new timer task.
   */
  protected TimerTask() {
  }

  /**
   * The action to be performed by this timer task.
   */
  protected abstract void action();

  /**
   * For the special case of immediate execution this implements
   * {@code Runnable}.
   */
  @Override
  public void run() {
    if (execute()) {
      action();
    }
  }

  /**
   * Cancels this timer task.
   */
  public boolean cancel() {
    return STATE_UPDATER.compareAndSet(this, SCHEDULED, CANCELLED);
  }

  public boolean execute() {
    return STATE_UPDATER.compareAndSet(this, SCHEDULED, EXECUTED);
  }

  public boolean schedule() {
    return STATE_UPDATER.compareAndSet(this, VIRGIN, SCHEDULED);
  }

  public boolean isCancelled() {
    return state == CANCELLED;
  }

  public boolean isScheduled() {
    return state == SCHEDULED;
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" +
      "state=" + state +
      ", executionTime=" + executionTime +
      '}';
  }

}
