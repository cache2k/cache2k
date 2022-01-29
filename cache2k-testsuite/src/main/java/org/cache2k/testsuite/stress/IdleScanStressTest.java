package org.cache2k.testsuite.stress;

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

import org.cache2k.pinpoint.stress.ThreadingStressTester;
import org.cache2k.testsuite.support.AbstractCacheTester;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * @author Jens Wilke
 */
public class IdleScanStressTest<K, V> extends AbstractCacheTester<K, V> {

  @Test
  public void test() {
    init(b -> b.idleScanTime(1, TimeUnit.MILLISECONDS));
    ThreadingStressTester tst = new ThreadingStressTester();
    Random random = new Random();
    tst.addTask(2, () -> {
      put(keys.get(random.nextInt(100_000)), v0);
    });
    tst.run();
  }

}
