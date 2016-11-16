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

import org.junit.Assert;

import javax.cache.processor.EntryProcessor;
import javax.cache.processor.MutableEntry;
import java.io.Serializable;

/**
 *
 * @param <K> key class
 * @param <V> value class
 * @param <T> process return type
 */
public class MultiArgumentHandlingEntryProcessor<K, V, T> implements EntryProcessor<K, V, T>, Serializable {
    private final T ret;

    public MultiArgumentHandlingEntryProcessor(T ret) {
        this.ret = ret;
    }

    @Override
    public T process(MutableEntry<K, V> entry, Object... arguments) {
        Assert.assertEquals("These", arguments[0]);
        Assert.assertEquals("are", arguments[1]);
        Assert.assertEquals("arguments", arguments[2]);
        Assert.assertEquals(1L, arguments[3]);

        return ret;
    }
}
