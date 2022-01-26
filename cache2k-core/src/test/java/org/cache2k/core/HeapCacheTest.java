package org.cache2k.core;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for HeapCache. We test only aspects not easily reachable via the public
 * API.
 *
 * @author Jens Wilke
 */
public class HeapCacheTest {

  @Test
  public void unsupported() {
    Cache<Object, Object> c = Cache2kBuilder.forUnknownTypes().build();
    assertThatCode(() ->
      c.requestInterface(HeapCache.class).getUserCache()
    ).isInstanceOf(UnsupportedOperationException.class);
  }

}
