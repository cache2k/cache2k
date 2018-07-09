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

/**
 * Set entry processor that generates a value based on the entries key.
 * The generated value is concatenation of valuePrefix + entry.getKey() + valuePrefix.
 *
 * @param <K> the key type
 *
 * @author Joe Fialli
 */
public class SetEntryWithComputedValueProcessor<K> implements EntryProcessor<K,
    String, String>, Serializable {
    private String valuePrefix;
    private String valuePostfix;

    public SetEntryWithComputedValueProcessor(String valuePrefix, String valuePostfix) {
        this.valuePrefix = valuePrefix;
        this.valuePostfix = valuePostfix;
    }

    @Override
    public String process(MutableEntry<K, String> entry, Object... arguments) {
        StringBuffer computedValue = new StringBuffer();
        if (valuePrefix != null) {
            computedValue.append(valuePrefix);
        }
        computedValue.append(entry.getKey().toString());
        if (valuePostfix != null) {
            computedValue.append(valuePostfix);
        }

        // Not trying to be efficient here.
        // For testing purposes in entry processor, follow the set with a get.
        // It would be more efficient to just return value that was passed to setValue.
        // This is testing the entry processor path of create or update an entry followed by an entry access.
        entry.setValue(computedValue.toString());
        return entry.getValue();
    }
}
