package org.cache2k.processor;

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
 * An invokable function to perform an atomic operation on a cache entry. The entry
 * processor is called via {@link org.cache2k.Cache#invoke} or
 * {@link org.cache2k.Cache#invokeAll}.
 *
 * <p>With the entry processor it is possible to realize arbitrary operation
 * semantics for an entry. For example, the method {@link org.cache2k.Cache#replaceIfEquals}
 * can be expressed with the entry processor as follows:
 *
 *  <pre> {@code
 *
 *    public boolean replaceIfEquals(final K key, final V oldValue, final V newValue) {
 *      EntryProcessor<K, V, Boolean> p = new EntryProcessor<K, V, Boolean>() {
 *        public Boolean process(MutableCacheEntry<K, V> entry) {
 *          if (!entry.exists()) {
 *            return false;
 *          }
 *          if (oldValue == null) {
 *            if (null != entry.getValue()) {
 *              return false;
 *            }
 *          } else {
 *            if (!oldValue.equals(entry.getValue())) {
 *              return false;
 *            }
 *          }
 *          entry.setValue(newValue);
 *          return true;
 *        }
 *      };
 *      return cache.invoke(key, p);
 *    }
 * }</pre>
 *
 * <p>For effects on the loader and writer and further details consult the documentation
 * on the {@link MutableCacheEntry}
 *
 * @author Jens Wilke
 * @see MutableCacheEntry
 * @see org.cache2k.Cache#invoke
 * @see org.cache2k.Cache#invokeAll
 */
@FunctionalInterface
public interface EntryProcessor<K, V, R> {

  /**
   * Examines or mutates an entry.
   *
   * <p><b>Important:</b> The method must not have any side effects except on the processed entry.
   * For one call to {@link org.cache2k.Cache#invoke} the method might be called several times.
   * Some methods of {@link MutableCacheEntry} throw exceptions that are consumed by the
   * cache to do asynchronous processing.
   *
   * <p><b>Caveat about modification time and expiry:</b> The point in time of the last
   * modification, in case the entry is modified, is after the method completes.
   * It is possible to set an alternative time with {@link MutableCacheEntry#setModificationTime(long)}
   *
   * <p>The cache is only modified, if the method completes without exception.
   *
   * @param entry the entry to examine or mutate. The reference is only valid within a method call
   * @return a user defined result, that will be passed through and returned
   * @throws Exception an arbitrary exception that will be wrapped into a
   *         {@link EntryProcessingException}.
   *         If an exception happens no modifications the cache content will not be altered.
   */
  R process(MutableCacheEntry<K, V> entry) throws Exception;

}
