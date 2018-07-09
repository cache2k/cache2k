/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jsr107.tck.integration;

import javax.cache.Cache;
import javax.cache.integration.CacheWriter;
import java.util.Collection;

/**
 * A {@link CacheWriter} implementation that always throws a
 * {@link UnsupportedOperationException}, regardless of the request.
 *
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public class FailingCacheWriter<K,V> extends RecordingCacheWriter<K,V> {

    @Override
    public void write(Cache.Entry<? extends K, ? extends V> entry) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void delete(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteAll(Collection<?> keys) {
        throw new UnsupportedOperationException();
    }
}
