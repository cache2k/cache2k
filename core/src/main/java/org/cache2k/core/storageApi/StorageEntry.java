package org.cache2k.core.storageApi;

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
 * @author Jens Wilke; created: 2014-03-27
 */
public interface StorageEntry {

  /** Key of the stored entry */
  Object getKey();

  /** Value of the stored entry */
  Object getValueOrException();

  /** Time the entry was last fetched or created from the original source */
  long getCreatedOrUpdated();

  /**
   * Time when the value is expired and is not allowed to be returned any more.
   * The storage needs to store this value. The storage may purge entries with
   * the time exceeded. 0 if not used and no expiry is requested.
   */
  long getValueExpiryTime();

  /**
   * Expiry time of the storage entry. After reaching this time, the entry should
   * be purged by the storage. The entry expiry time will get updated by
   * the the cache when the value is not expired and the entry is still
   * accessed. Returns 0 if not used.
   */
  long getEntryExpiryTime();

}
