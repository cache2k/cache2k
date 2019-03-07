package org.cache2k.integration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
 * A loaded value may be wrapped by the loader to transport auxiliary information
 * to the cache. Use the utility methods in {@link Loaders}
 *
 * @author Jens Wilke
 */
public abstract class LoadDetail<V> {

  private Object value;

  public LoadDetail(final Object valueOrWrapper) {
    value = valueOrWrapper;
  }

  @SuppressWarnings("unchecked")
  public V getValue() {
    if (value instanceof LoadDetail) {
      return ((LoadDetail<V>) value).getValue();
    }
    return (V) value;
  }

  @SuppressWarnings("unchecked")
  public LoadDetail<V> getNextInChain() {
    if (value instanceof LoadDetail) {
      return ((LoadDetail<V>) value);
    }
    return null;
  }

}
