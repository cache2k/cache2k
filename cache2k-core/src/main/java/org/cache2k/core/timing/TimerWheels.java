package org.cache2k.core.timing;

/*-
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
 * Hierarchical timer wheel implementation. The implementation is flexible and
 * can work with variable delta time per time slot and variable slots per wheel
 * level.
 *
 * <p>This implementation is rather simple and has opportunities to improve performance.
 *
 * @author Jens Wilke
 */
public class TimerWheels implements TimerStructure {

  private Wheel wheel;

  public TimerWheels(long startTime, long delta, int slots) {
    wheel = new Wheel(startTime, delta, slots);
  }

  public boolean schedule(TimerTask task, long time) {
    task.time = time;
    return wheel.schedule(task);
  }

  public void cancelAll(TimerTask t) {
    t.cancel();
  }

  public void cancelAll() {
    wheel.cancel();
  }

  public TimerTask removeNextToRun(long time) {
    TimerTask t = wheel.removeNextToRun(time);
    return t;
  }

  public long nextRun() {
    return wheel.nextToRun();
  }

  static class Wheel {

    private Wheel up;
    private long noon;
    private long oneBeforeNextNoon;
    private long delta;
    private TimerTask[] slots;
    private  int index;

    Wheel(long time, long delta, int slotCount) {
      this.delta = delta;
      initArray(slotCount);
      atNoon(time);
    }

    /**
     * Called when we reach noon to reset the index hand.
     */
    private void atNoon(long time) {
      index = 0;
      noon = time;
      oneBeforeNextNoon = time + delta * slots.length - 1;
      if (time < 0) {
        throw new IllegalArgumentException("maximum reached");
      }
    }

    private void initArray(int slotCount) {
      slots = new TimerTask[slotCount];
      for (int i = 0; i < slots.length; i++) {
        slots[i] = new TimerTask.Sentinel();
      }
    }

    /**
     * Reinitialize slots and discard higher hierarchies.
     */
    private void cancel() {
      up = null;
      initArray(slots.length);
    }

    /**
     * Time, when all tasks for the given slot index can be executed.
     */
    long executionTime(int i) {
      return noon + delta * i + delta - 1;
    }

    /**
     * Search for non empty time slot and return the time, when
     * the slot can be executed. For simplicity we don't recurse
     * into higher hierarchies, so this method is only called
     * at the lowest hierarchy.
     */
    long nextToRun() {
      for (int i = index; i < slots.length; i++) {
        if (!slots[i].isEmpty()) {
          return executionTime(i);
        }
      }
      if (up == null) {
        return Long.MAX_VALUE;
      }
      return executionTime(slots.length);
    }

    /**
     * If execution time for the current slot is reached, return the tasks in it.
     * We don't need to compare the actual time in the task.
     * If all tasks are completed within the slot and the time is past the
     * slot execution time, we move the slot index forward.
     */
    public TimerTask removeNextToRun(long time) {
      long hand = executionTime(index);
      if (time >= hand) {
        while (true) {
          TimerTask head = slots[index];
          if (!head.isEmpty()) {
            TimerTask t = head.next;
            t.remove();
            return t;
          }
          hand = hand + delta;
          if (time < hand) {
            return null;
          }
          moveHand();
        }
      }
      return null;
    }

    /**
     * Move to the next time slot. If we completed a circle, refill from the
     * upper hierarchy.
     */
    private void moveHand() {
      index++;
      if (index >= slots.length) {
        atNoon(oneBeforeNextNoon + 1);
        refill();
      }
    }

    /**
     * Refill this timer wheel from the upper hierarchy, sorting
     * all tasks into their slots.
     */
    private void refill() {
       TimerTask t;
       long limit = oneBeforeNextNoon;
       Wheel up = this.up;
       if (up == null) { return; }
       while ((t = up.removeNextToRun(limit)) != null) {
         insert(t);
       }
    }

    /**
     * If within bounds insert into this wheel or delegate to the
     * next hierarchy level. This will create more levels until
     * the time is covered by that hierarchy level.
     */
    private boolean schedule(TimerTask t) {
      long hand = executionTime(index - 1);
      if (t.time <= hand) {
        return false;
      } else if (t.time <= oneBeforeNextNoon) {
        insert(t);
        return true;
      } else {
        Wheel up = this.up;
        if (up == null) {
          this.up = up = new Wheel(oneBeforeNextNoon + 1, delta * slots.length, slots.length);
        }
        return up.schedule(t);
      }
    }

    /**
     * Insert into the proper time slot.
     */
    private void insert(TimerTask t) {
      int idx = (int) ((t.time - noon) / delta);
      slots[idx].insert(t);
    }

  }

}
