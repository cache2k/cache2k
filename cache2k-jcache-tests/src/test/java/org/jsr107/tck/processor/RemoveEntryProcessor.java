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



package org.jsr107.tck.processor;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Remove entry processor
 * @param <K>  key type
 * @param <V>  value type
 * @param <T>  process return type
 */
public class RemoveEntryProcessor<K, V, T> implements EntryProcessor<K, V,
    T>, Serializable {

    private final boolean assertExists;

    public RemoveEntryProcessor(){
        this(false);
    }

    public RemoveEntryProcessor(boolean assertExists) {
        this.assertExists = assertExists;
    }

    @Override
    public T process(MutableEntry<K, V> entry, Object... arguments) {
        T result = null;
        if (assertExists) {
            assertTrue(entry.exists());
            result = (T)entry.getValue();
        }
        entry.remove();
        assertFalse(entry.exists());

        return result;
    }
}
