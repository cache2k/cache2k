package org.cache2k;

/*
 * #%L
 * cache2k API only package
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

/**
 * Base builder which defines top level methods
 * delegating to the root configuration builder.
 *
 * @author Jens Wilke; created: 2014-04-19
 * @param <C> configuration bean type
 */
public abstract class BaseAnyBuilder<K, T, C>
  implements AnyBuilder<K, T, C> {

  protected CacheBuilder<K, T> root;

  BaseAnyBuilder() { }

  void setRoot(CacheBuilder<K, T> v) {
    root = v;
  }

  /**
   * Adds persistence to the cache or returns a previous added persistence storage
   * configuration node. The persistence configuration is taken from a default
   * configuration. Ideally speaking requesting persistence means that the
   * cache contains data that is costly to reproduce and/or needs a big
   * amount of storage which is not available within the java heap.
   */
  public StorageConfiguration.Builder<K, T, ?> persistence() { return root.persistence(); }

  public CacheBuilder<K, T> root() {
    return root;
  }

  public Cache<K, T> build() {
    return root.build();
  }

}
