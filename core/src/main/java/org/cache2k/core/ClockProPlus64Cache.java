package org.cache2k.core;

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
 * Specialization for 64 bit systems.
 */
public final class ClockProPlus64Cache<K, V>  extends ClockProPlusCache<K, V> {

  /**
   * Just increment the hit counter on 64 bit systems. Writing a 64 bit value is atomic on 64 bit systems
   * but not on 32 bit systems. Of course the counter update itself is not an atomic operation, which will
   * cause some missed hits. The 64 bit counter is big enough that it never wraps around in my projected
   * lifetime...
   */
  @Override
  protected void recordHit(Entry e) {
    e.hitCnt++;
  }

}
