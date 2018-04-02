package org.cache2k.test.core;

/*
 * #%L
 * cache2k implementation
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

import org.cache2k.Cache2kBuilder;
import org.cache2k.testing.category.FastTests;
import org.cache2k.test.util.CacheRule;
import org.cache2k.test.util.IntCacheRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.experimental.categories.Category;

import static org.cache2k.test.core.StaticUtil.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class RejectNullValueWiredCacheTest extends RejectNullValueTest {

  @ClassRule
  public final static CacheRule<Integer, Integer> staticTarget = new IntCacheRule()
    .config(new CacheRule.Specialization<Integer, Integer>() {
      @Override
      public void extend(final Cache2kBuilder<Integer, Integer> b) {
        configureRejectNull(b);
        enforceWiredCache(b);
      }
    });

  @Before
  public void setup() {
    target = staticTarget;
  }

}
