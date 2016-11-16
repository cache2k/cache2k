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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Replace entry processor
 * @param <K>  key type
 * @param <V>  value type
 * @param <T>  process return type
 */
public class ReplaceEntryProcessor<K, V, T> implements EntryProcessor<K, V,
    T>, Serializable {
    private final V newValue;
    private final V oldValue;

    public ReplaceEntryProcessor(V oldValue, V newValue) {
        this.newValue = newValue;
        this.oldValue = oldValue;
    }

    @Override
    public T process(MutableEntry<K, V> entry, Object... arguments) {
        assertTrue(entry.exists());
        V value1 = entry.getValue();
        assertEquals(oldValue, entry.getValue());
        entry.setValue(newValue);
        assertTrue(entry.exists());
        assertEquals(newValue, entry.getValue());

        return (T) value1;
    }
}
