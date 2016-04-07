package org.cache2k.storage;

/*
 * #%L
 * cache2k core package
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
