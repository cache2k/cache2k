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
 * Storage supports an efficient way to update the expiry time of an entry.
 *
 * @author Jens Wilke; created: 2014-06-12
 */
public interface EntryExpiryUpdateableStorage extends CacheStorage {

  /**
   * Update the expiry time of a stored entry. If the entry is not
   * existing, the method returns without exception.
   */
  public void updateEntryExpireTime(Object key, long _millis) throws Exception;

}
