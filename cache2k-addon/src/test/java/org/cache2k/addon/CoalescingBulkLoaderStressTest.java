package org.cache2k.addon;

/*-
 * #%L
 * cache2k addon
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
import org.cache2k.pinpoint.stress.ThreadingStressTester;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class CoalescingBulkLoaderStressTest {

  @Test
  public void test() {
    CoalescingBulkLoaderTest.IdentBulkLoader ibl =
      new CoalescingBulkLoaderTest.IdentBulkLoader();
    Cache<Integer, Integer> c =
      Cache2kBuilder.of(Integer.class, Integer.class)
        .bulkLoader((keys, context, callback) -> {
          if (keys.hashCode() % 2 == 0) {
            context.getExecutor().execute(() -> {
              ibl.loadAll(keys, context, callback);
            });
            return;
          }
          ibl.loadAll(keys, context, callback);
        })
        .enableWith(CoalescingBulkLoaderSupport.class, b -> b
          .maxBatchSize(100)
          .maxDelay(2, TimeUnit.MILLISECONDS)
        )
        .entryCapacity(10)
        .build();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addTask(3, () -> {
      Random r = new Random();
      for (int i = 0; i < 10; i++) {
        int k = r.nextInt(20);
        assertThat(c.get(k)).isEqualTo(k);
      }
    });
    tst.addTask(() -> {
      Random r = new Random();
      c.loadAll(asList(r.nextInt(20), r.nextInt(20)));
    });
    tst.setDoNotInterrupt(true);
    tst.run();
    c.close();
  }
}
