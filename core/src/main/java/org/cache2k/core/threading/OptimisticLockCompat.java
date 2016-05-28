package org.cache2k.core.threading;

/*
 * #%L
 * cache2k core
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

import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * @author Jens Wilke
 */
public class OptimisticLockCompat implements OptimisticLock {

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

  @Override
  public long tryOptimisticRead() {
    return sync.getStamp();
  }

  @Override
  public boolean validate(final long stamp) {
    return sync.getStamp() == stamp;
  }

  @Override
  public void unlockRead(final long stamp) {
    sync.release(DUMMY);
  }

  @Override
  public void unlockWrite(final long stamp) {
    sync.release(DUMMY);
  }

  @Override
  public boolean canCheckHolder() {
    return true;
  }

  @Override
  public boolean isHoldingWriteLock() {
    return sync.isHeldExclusively();
  }

  private static final class Sync extends AbstractQueuedSynchronizer {

    @Override
    protected boolean tryAcquire(int acquires) {
      int _state = getState();
      if ((_state & 1) == 0) {
        int _nextLockedState = _state + 1;
        if (compareAndSetState(_state, _nextLockedState)) {
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
      setState(getState() + 1);
      return true;
    }

    @Override
    public boolean isHeldExclusively() {
      return isLocked() && (getExclusiveOwnerThread() == Thread.currentThread());
    }

    private boolean isLocked() {
      return (getState() & 1) == 1;
    }

    private int getStamp() {
      return getState();
    }

  }

}
