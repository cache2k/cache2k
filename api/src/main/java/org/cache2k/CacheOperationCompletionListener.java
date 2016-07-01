package org.cache2k;

/*
 * #%L
 * cache2k API
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

import java.util.EventListener;

/**
 * A listener implemented by the applications to get notifications after a
 * {@link Cache#loadAll} or (@link Cache#reload} has been completed.
 *
 * @author Jens Wilke
 */
public interface CacheOperationCompletionListener extends EventListener {

  /**
   * Signals the completion of a {@link Cache#loadAll} or (@link Cache#reload} operation.
   */
  void onCompleted();

  /**
   * The operation could not completed, because of an error.
   *
   * <p>In the current implementation, there is no condition which raises a call to this method.
   * Errors while loading a value, will be delayed and propagated when the respective key
   * is accessed. This is subject to the resilience configuration.
   *
   * <p>The method may be used in the future for some general failure condition during load.
   * Applications should propagate the exception properly and not only log it.
   */
  void onException(Throwable exception);

}
