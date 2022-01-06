package org.cache2k.core.experimentalApi;

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

import java.util.EventListener;
import java.util.concurrent.Executor;

/**
 * @author Jens Wilke
 */
public interface AsyncCacheWriter<K, V> {

  void write(K key, V value, Callback callback, Executor ex);

  void remove(K key, Callback callback, Executor ex);

  /**
   * @author Jens Wilke
   */
  interface Callback extends EventListener {

    void onWriteSuccess();
    void onWriteFailure(Throwable ex);

  }
}
