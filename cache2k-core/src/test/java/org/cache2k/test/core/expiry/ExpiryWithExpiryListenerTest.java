package org.cache2k.test.core.expiry;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheEntry;
import org.cache2k.event.CacheEntryExpiredListener;
import org.cache2k.testing.category.FastTests;
import org.junit.Ignore;
import org.junit.experimental.categories.Category;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class) @Ignore("prune")
public class ExpiryWithExpiryListenerTest extends ExpiryTest {

  @Override
  protected <K, T> Cache2kBuilder<K, T> builder(Class<K> k, Class<T> t) {
    return
      super.builder(k, t).addListener(new CacheEntryExpiredListener<K, T>() {
      @Override
      public void onEntryExpired(Cache<K, T> cache, CacheEntry<K, T> entry) {

      }
    });

  }

}
