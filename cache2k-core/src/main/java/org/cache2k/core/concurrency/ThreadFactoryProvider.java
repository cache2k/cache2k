package org.cache2k.core.concurrency;

/*-
 * #%L
 * cache2k core implementation
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

import java.util.concurrent.ThreadFactory;

/**
 * Provider interface for a thread factory. This makes it possible to change
 * the thread factory via the {@link org.cache2k.core.api.InternalConfig}.
 *
 * @author Jens Wilke
 */
public interface ThreadFactoryProvider {

  ThreadFactoryProvider DEFAULT = new DefaultThreadFactoryProvider();
  
  /**
   * Construct a new thread factory for the pool.
   */
  ThreadFactory newThreadFactory(String namePrefix);

}
