package org.cache2k.jmx;

/*
 * #%L
 * cache2k jmx api definitions
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
 * @author Jens Wilke; created: 2013-12-20
 */
@SuppressWarnings("unused")
public interface CacheManagerMXBean {

  int getThreadsInPool();

  int getPeakThreadsInPool();

  /**
   * Combined health of all caches.
   *
   * @see org.cache2k.jmx.CacheInfoMXBean#getAlert()
   */
  int getAlert();

  /**
   * Clear all associated caches.
   */
  void clear();

  String getVersion();

  String getBuildNumber();

}
