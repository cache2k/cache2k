package org.cache2k.impl;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2015 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

/**
 * Specialization for 64 bit systems.
 */
public final class ClockProPlus64Cache<K, T>  extends ClockProPlusCache<K, T> {

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
