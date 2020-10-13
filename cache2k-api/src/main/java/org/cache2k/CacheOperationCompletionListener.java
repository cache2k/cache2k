package org.cache2k;

/*
 * #%L
 * cache2k API
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

import java.util.EventListener;

/**
 * A listener implemented by the cache client to get notification about the
 * completion of a load or prefetch operation.
 *
 * @author Jens Wilke
 * @see Cache#loadAll(Iterable, CacheOperationCompletionListener)
 * @deprecated to be removed in 2.2
 */
@Deprecated
public interface CacheOperationCompletionListener extends EventListener {

  /**
   * Signals the completion of a {@link Cache#loadAll}, {@link Cache#reloadAll} or
   * {@link Cache#prefetchAll} operation.
   */
  void onCompleted();

  /**
   * The operation could not completed, because of an error.
   *
   * <p>In the current implementation, there is no condition which raises a call to this method.
   * Errors while loading a value, will be delayed and propagated when the respective key
   * is accessed. This is subject to the resilience configuration.
   */
  void onException(Throwable exception);

}
