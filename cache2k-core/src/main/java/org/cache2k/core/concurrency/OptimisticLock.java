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

/**
 * Interface for a lock that allows an optimistic read by comparing
 * a stamp value. The basic interface is identical to the Java 8
 * {@link java.util.concurrent.locks.StampedLock}
 *
 * <p>The implementation may support optimistic locking if available at
 * the platform. If not supported, {@link #validate(long)} returns always false.
 * This allows the identical usage pattern for both variants.
 *
 * @author Jens Wilke
 */
public interface OptimisticLock {

  /**
   * Exclusively acquires the lock, blocking if necessary
   * until available.
   *
   * @return a stamp that can be used to unlock or convert mode
   */
  long writeLock();

  /**
   * Non-exclusively acquires the lock, blocking if necessary
   * until available.
   *
   * @return a stamp that can be used to unlock or convert mode
   */
  long readLock();

  /**
   * Returns a stamp that can later be validated, or zero
   * if exclusively locked. Also ensures that all stores that
   * happened before {@link #unlockWrite(long)} become visible.
   *
   * @return a stamp, or zero if exclusively locked
   */
  long tryOptimisticRead();

  /**
   * Returns true if the lock has not been exclusively acquired
   * since issuance of the given stamp. Always returns false if the
   * stamp is zero. Always returns true if the stamp represents a
   * currently held lock. Invoking this method with a value not
   * obtained from {@link #tryOptimisticRead} or a locking method
   * for this lock has no defined effect or result.
   *
   * @param stamp a stamp
   * @return {@code true} if the lock has not been exclusively acquired
   * since issuance of the given stamp; else false
   */
  boolean validate(long stamp);

  /**
   * If the lock state matches the given stamp, releases the
   * non-exclusive lock.
   *
   * @param stamp a stamp returned by a read-lock operation
   * @throws IllegalMonitorStateException if the stamp does
   * not match the current state of this lock
   */
  void unlockRead(long stamp);

  /**
   * If the lock state matches the given stamp, releases the
   * exclusive lock.
   *
   * @param stamp a stamp returned by a write-lock operation
   * @throws IllegalMonitorStateException if the stamp does
   * not match the current state of this lock
   */
  void unlockWrite(long stamp);

}
