package org.cache2k.extra.config.test;

/*
 * #%L
 * cache2k config file support
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

import org.cache2k.Cache;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryUpdatedListener;

/**
 * @author Jens Wilke
 */
public class UpdateListener implements CacheEntryUpdatedListener<Object, Object> {

  @Override
  public void onEntryUpdated(Cache<Object, Object> cache,
                             CacheEntry<Object, Object> currentEntry,
                             CacheEntry<Object, Object> newEntry) {
  }

}
