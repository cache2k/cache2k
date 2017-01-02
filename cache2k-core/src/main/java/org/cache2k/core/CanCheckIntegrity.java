package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2017 headissue GmbH, Munich
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
 * Cache supports checking its internal integrity.
 *
 * @author Jens Wilke; created: 2013-07-11
 */
public interface CanCheckIntegrity {

  /**
   * Cache checks its internal integrity. This is a expansive operation because it
   * may traverse all cache entries. Used for testing.
   *
   * @throws IllegalStateException if integrity problem is found
   */
  void checkIntegrity();

}
