package org.cache2k.core;

/*
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

/**
 * Consistently this exception is thrown, when an operation detects that the
 * cache is closed.
 *
 * <p>Rationale: It is a subtype of {@link java.lang.IllegalStateException}
 * and not a {@link org.cache2k.CacheException} since the JSR107 defines
 * it. On the API level we specify {@link IllegalStateException} to not
 * introduce a new exception type. Moving this to the API level, makes it more
 * difficult to introduce improvements here, since we need to keep stuff for BC.
 * Better keep it internal.
 *
 * @author Jens Wilke
 */
public class CacheClosedException extends IllegalStateException {

  public CacheClosedException() { }

  /**
   * Included manager and cache name in the detail message, preferred.
   */
  public CacheClosedException(Cache cache) {
    super(BaseCache.nameQualifier(cache));
  }

}
