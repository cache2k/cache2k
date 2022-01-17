package org.cache2k.testsuite.api;

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
import org.cache2k.io.AsyncBulkCacheLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class BulkCacheLoaderTest {

  @Test
  public void test() {
    List<Object> keys = new ArrayList<>();
    AtomicReference<Throwable> reference = new AtomicReference<>();
    AsyncBulkCacheLoader.BulkCallback<Integer, Integer> cb =
      new AsyncBulkCacheLoader.BulkCallback() {
        @Override
        public void onLoadSuccess(Map data) { }

        @Override
        public void onLoadSuccess(Object key, Object value) { }

        @Override
        public void onLoadFailure(Throwable exception) { }

        @Override
        public void onLoadFailure(Object key, Throwable exception) {
          keys.add(key);
          reference.set(exception);
        }
      };
    cb.onLoadFailure(Arrays.asList(1, 2 ,3), new RuntimeException());
    assertThat(keys).containsExactlyInAnyOrder(1, 2, 3);
    assertThat(reference.get()).isNotNull();
  }

}
