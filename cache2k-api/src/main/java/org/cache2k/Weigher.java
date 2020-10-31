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

/**
 * Allows to give cached values a weight and limit the cache capacity by total weight.
 *
 * @author Jens Wilke
 */
public interface Weigher<K, V> extends Customization<K, V> {

  /**
   * Returns a weight for the given cached value. This will be called after a value is
   * inserted or updated.
   *
   * <p>The cache implementations may derive an approximate value which has less precision.
   * The total weight in the statistics represents an approximation as well.
   *
   * @return a positive long value representing the relative weight in comparison to the other
   * entries in the cache.
   */
  int weigh(K key, V value);

}
