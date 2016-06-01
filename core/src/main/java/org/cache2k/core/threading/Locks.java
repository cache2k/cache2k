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
public class Locks {

  private static Class<? extends OptimisticLock> optimisticLockImplementation;

  public static OptimisticLock newOptimistic() {
    if (optimisticLockImplementation == null) {
      initializeOptimisticLock();
    }
    try {
      return optimisticLockImplementation.newInstance();
    } catch (Exception ex) {
      throw new Error(ex);
    }
  }

  private static void initializeOptimisticLock() {
    try {
      optimisticLockImplementation = OptimisticLockStamped.class;
      return;
    } catch (Exception ignore) {
    }
    optimisticLockImplementation = NonOptimisticLock.class;
  }

}
