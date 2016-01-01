package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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
 * Optional interface for a {@link CacheStorage} if the storage needs to flush any
 * unwritten data to make it persistent.
 *
 * @author Jens Wilke; created: 2014-06-09
 */
public interface FlushableStorage extends CacheStorage {

  /**
   * Flush any unwritten information to disk. The method returns when the flush
   * is finished and everything is written. The cache is not protecting the
   * storage from concurrent read/write operation while the flush is performed.
   *
   * <p/>A flush is initiated by client request or on regular intervals after a
   * modification has happened.
   */
  public void flush(FlushableStorage.FlushContext ctx, long now) throws Exception;

  interface FlushContext extends CacheStorage.MultiThreadedContext {

  }

}
