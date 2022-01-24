package org.cache2k.testsuite;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.assertj.core.api.Assertions;
import org.cache2k.operation.CacheControl;
import org.cache2k.operation.TimeReference;
import org.cache2k.operation.Weigher;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class CacheControlTest<K, V> extends AbstractCacheTester<K, V> {

  @Test
  public void initial() {
    init();
    assertThat(CacheControl.of(cache).getTimeReference()).isSameAs(TimeReference.DEFAULT);
    waitFor(CacheControl.of(cache).close());
    assertThat(cache.isClosed()).isTrue();
  }

  @Test
  public void removeAll() {
    init();
    put(k0, v0);
    put(k1, v1);
    assertThat(asMap().size()).isEqualTo(2);
    waitFor(CacheControl.of(cache).removeAll());
    assertThat(asMap().size()).isEqualTo(0);
  }

  @Test
  public void getCapacityLimitWithWeigher() {
    init(b -> b
      .maximumWeight(1234)
      .weigher((key, value) -> 1)
      .strictEviction(true)
    );
    assertThat(CacheControl.of(cache).getCapacityLimit()).isEqualTo(1234);
  }

}
