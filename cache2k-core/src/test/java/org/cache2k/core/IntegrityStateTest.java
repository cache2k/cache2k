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
import org.cache2k.core.api.InternalCache;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class IntegrityStateTest {

  @Test
  public void initial() {
    IntegrityState is = new IntegrityState();
    assertThat(is.isFailure()).isFalse();
    assertThat(is.getFailingChecks()).isEqualTo("");
  }

  @Test
  public void allOkay() {
    IntegrityState is = new IntegrityState()
      .check("boolean", true)
      .check("boolean", "why", true)
      .checkEquals("int", 1, 1)
      .checkEquals("long", (long) 1, 1);
    assertThat(is.isFailure()).isFalse();
    assertThat(is.getFailingChecks()).isEqualTo("");
  }

  @Test
  public void allFail() {
    IntegrityState is = new IntegrityState()
      .check("boolean1", false)
      .group("")
      .check("boolean2", "why", false)
      .group(null)
      .check("boolean3", false)
      .checkEquals("int", 1, 2)
      .checkEquals("long", (long) 1, 2)
      .group("group2")
      .checkEquals("int", 1, 2)
      .checkEquals("long", (long) 1, 2);
    assertThat(is.isFailure()).isTrue();
    assertThat(is.getFailingChecks())
      .isEqualTo("boolean1, boolean2(why), boolean3, int(1==2), long(1==2), " +
          "group2:int(1==2), group2:long(1==2)");
  }

  @Test
  public void throwError() {
    IntegrityState is = new IntegrityState()
      .check("boolean", false);
    Cache<?, ?> cache = Cache2kBuilder.forUnknownTypes().build();
    assertThatCode(() ->
      HeapCache.throwErrorOnIntegrityFailure(is, (InternalCache) cache))
      .isInstanceOf(Error.class);
    cache.close();;
  }

}
