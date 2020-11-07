package org.cache2k.event;

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
import java.util.concurrent.CompletableFuture;

/**
 * Listeners for lifecycle events of a cache.
 *
 * @author Jens Wilke
 */
public interface CacheLifecycleListener extends EventListener {

  /**
   * Single instance of completed future.
   */
  CompletableFuture<Void> COMPLETE = new CompletableFuture<Void>() {
    public CompletableFuture<Void> newIncompleteFuture() {
      return this;
    }
    public void obtrudeValue(Void value) { throw new UnsupportedOperationException(); }
    public void obtrudeException(Throwable ex) { throw new UnsupportedOperationException(); }
  };

}
