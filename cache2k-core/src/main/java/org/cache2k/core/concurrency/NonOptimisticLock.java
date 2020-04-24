package org.cache2k.core.concurrency;

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

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Fallback locking mechanism when {@code StampedLock} is not available. This
 * lock denies optimistic locking and uses a single write lock.
 *
 * <p>Optimistic locking is only possible on a Java 8 VM that support the
 * {@code loadFence} instructions. Otherwise loads might get reordered after
 * the {@code validate}, causing wrong results.
 *
 * @author Jens Wilke
 */
public class NonOptimisticLock implements OptimisticLock {

  private static final int DUMMY = 4711;

  private Sync sync = new Sync();

  @Override
  public long readLock() {
    sync.acquire(DUMMY);
    return DUMMY;
  }

  @Override
  public long writeLock() {
    sync.acquire(DUMMY);
    return DUMMY;
  }

  /**
   * This is a read access on the lock state for data visibility.
   * Normal stamped lock return 0 for exclusive lock.
   */
  @Override
  public long tryOptimisticRead() {
    return sync.getStamp() == Sync.LOCKED ? 0 : 1;
  }

  /**
   * Always false, optimistic locking not supported.
   */
  @Override
  public boolean validate(final long stamp) {
    return false;
  }

  @Override
  public void unlockRead(final long stamp) {
    sync.release(DUMMY);
  }

  @Override
  public void unlockWrite(final long stamp) {
    sync.release(DUMMY);
  }

  private static final class Sync extends AbstractQueuedSynchronizer {

    private static final int UNLOCKED = 0;
    private static final int LOCKED = 1;

    @Override
    protected boolean tryAcquire(int acquires) {
      int _state = getState();
      if (_state == UNLOCKED) {
        if (compareAndSetState(_state, LOCKED)) {
          setExclusiveOwnerThread(Thread.currentThread());
          return true;
        }
      }
      return false;
    }

    @Override
    protected boolean tryRelease(int releases) {
      if (Thread.currentThread() != getExclusiveOwnerThread()) {
        throw new IllegalMonitorStateException();
      }
      setExclusiveOwnerThread(null);
      setState(UNLOCKED);
      return true;
    }

    @Override
    public boolean isHeldExclusively() {
      return getState() == LOCKED && (getExclusiveOwnerThread() == Thread.currentThread());
    }

    private int getStamp() {
      return getState();
    }

  }

}
