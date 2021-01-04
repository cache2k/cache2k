package org.cache2k.core.timing;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
 * Task for the simple timer
 *
 * @author Jens Wilke
 */
public abstract class TimerTask implements Runnable {

  private static final long UNSCHEDULED = -1;
  long time = UNSCHEDULED;
  TimerTask next, prev = null;
  void insert(TimerTask t) { t.next = next; t.prev = this; next.prev = t; next = t; }
  void remove() { prev.next = next; next.prev = prev; next = prev = null; }
  void execute() { prev = this; }
  boolean isEmpty() { return next == this; }

  static class Sentinel extends TimerTask {
    { next = prev = this; }
    @Override protected void action() { }
  }

  /**
   * The action to be performed by this timer task.
   */
  protected abstract void action();

  protected boolean cancel() {
    if (next != null) {
      remove();
      return true;
    }
    return false;
  }

  /**
   * For the special case of immediate execution this implements
   * {@code Runnable}.
   */
  @Override
  public void run() {
    if (isExecuted()) {
      action();
    }
  }

  public boolean isUnscheduled() {
    return time == UNSCHEDULED;
  }

  public boolean isExecuted() {
    return next == null && prev == this;
  }

  public boolean isCancelled() {
    return prev == null;
  }

  public boolean isScheduled() {
    return time != UNSCHEDULED && !isCancelled();
  }

  public String getState() {
    if (isUnscheduled()) {
      return "unscheduled";
    }
    if (isCancelled()) {
      return "cancelled";
    }
    if (isExecuted()) {
      return "executed";
    }
    return "scheduled";
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName() + "{" +
      "state=" + getState() +
      ", time=" + time +
      '}';
  }

}
