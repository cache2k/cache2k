package org.cache2k.core.concurrency;

/*
 * #%L
 * cache2k core implementation
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
 * Internal factory for lock implementations.
 *
 * @author Jens Wilke
 */
public class Locks {

  private static final boolean USE_OPTIMISTIC_LOCK;

  static {
    USE_OPTIMISTIC_LOCK = initializeOptimisticLock();
  }

  /**
   * Returns a new lock implementation depending on the platform support.
   */
  public static OptimisticLock newOptimistic() {
    if (USE_OPTIMISTIC_LOCK) {
      return new OptimisticLockStamped();
    } else {
      return new NonOptimisticLock();
    }
  }

  /**
   * Loading of StampedLock will throw an error, if not available.
   * Fallback to NonOptimisticLock in this case (in android).
   */
  private static boolean initializeOptimisticLock() {
    try {
      if (System.getProperty(NonOptimisticLock.class.getName()) == null) {
        new OptimisticLockStamped();
        return true;
      }
    } catch (NoClassDefFoundError ignore) {
    }
    return false;
  }

}
