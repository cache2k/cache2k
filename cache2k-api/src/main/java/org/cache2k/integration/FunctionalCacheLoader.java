package org.cache2k.integration;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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
 * Functional cache loader interface to be used when only a single method is
 * used. See the {@link CacheLoader} for full documentations.
 *
 * <p>Rationale: Have a functional interface for Java 8 lambda usage, while
 * still maintain the abstract cache loader class that is extensible with
 * new methods for Java 6.
 *
 * @author Jens Wilke
 * @see CacheLoader
 */
public interface FunctionalCacheLoader<K,V> {

  /**
   * @see CacheLoader#load(Object)
   */
  V load(K key) throws Exception;

}
