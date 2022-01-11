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

import org.cache2k.annotation.Nullable;

/**
 * Result tuple for {@code Cache.invokeAll()}.
 *
 * @see EntryProcessor
 * @see org.cache2k.Cache#invokeAll
 * @author Jens Wilke
 */
public interface EntryProcessingResult<@Nullable R> {

  /**
   * Result of entry processing.
   *
   * @throws EntryProcessingException if an exception occurred during processing.
   */
  @Nullable R getResult();

  /**
   * Original exception from entry processing or {@code null} if no exception occurred.
   * If this is {@code null}, {@link #getResult} will not throw an exception.
   */
  @Nullable Throwable getException();

}
