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

/**
 * @author Jens Wilke
 */
public interface OptimisticLock {

  long writeLock();

  long readLock();

  long tryOptimisticRead();

  boolean validate(long stamp);

  void unlockRead(long stamp);

  void unlockWrite(long stamp);

  /**
   * This lock supports {@link #isHoldingWriteLock()}
   */
  boolean canCheckHolder();

  /**
   * Checks whether current thread is holding the write lock.
   */
  boolean isHoldingWriteLock();

}
