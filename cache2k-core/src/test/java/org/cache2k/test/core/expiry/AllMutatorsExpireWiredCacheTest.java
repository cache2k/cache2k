package org.cache2k.test.core.expiry;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2022 headissue GmbH, Munich
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.testing.category.SlowTests;
import org.cache2k.test.core.StaticUtil;
import org.junit.experimental.categories.Category;

/**
 * Test expiry on mutators for the wired cache.
 *
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class AllMutatorsExpireWiredCacheTest extends AllMutatorsExpireTest {

  @Override
  protected <K, T> Cache2kBuilder<K, T> builder(Class<K> k, Class<T> t) {
    return StaticUtil.enforceWiredCache(super.builder(k, t));
  }

  public AllMutatorsExpireWiredCacheTest(Pars p) {
    super(p);
  }

}
