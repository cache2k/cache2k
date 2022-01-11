package org.cache2k.processor;

/*-
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.CacheException;

/**
 * Used by the entry processor to abort the processing to carry out
 * some, possibly asynchronous, processing.
 *
 * @author Jens Wilke
 */
public class RestartException extends CacheException {

  /**
   * Used for internal flow control, no need to carry a
   * message or stack trace. Disabling the stack trace speeds up processing.
   * @see <a href="https://github.com/cache2k/cache2k/issues/170"/>
   */
  protected RestartException() {
    super(null, null, false, false);
  }

}
