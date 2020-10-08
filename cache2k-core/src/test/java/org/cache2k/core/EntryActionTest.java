package org.cache2k.core;

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

import org.cache2k.core.operation.ExaminationEntry;
import org.cache2k.core.operation.Progress;
import org.cache2k.core.operation.Semantic;
import org.cache2k.test.util.TestingBase;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jens Wilke
 */
public class EntryActionTest extends TestingBase {

  @Test
  public void testAbort() {
    BaseCache<Integer, Integer> bc = (BaseCache<Integer, Integer>) builder().build();
    Semantic op = new Semantic<Integer, Integer, Object>() {
      final AtomicInteger count = new AtomicInteger();

      @Override
      public void start(Progress<Integer, Integer, Object> c) {
        c.wantData();
      }

      /**
       * Called twice to guarantee atomic behavior after locking
       * Call noMutation on second call to simulate a change in the entry that
       * would result in no mutation.
       */
      @Override
      public void examine(Progress<Integer, Integer, Object> c,
                          ExaminationEntry<Integer, Integer> e) {
        if (count.getAndIncrement() % 2 == 0) {
          c.wantMutation();
        } else {
          c.noMutation();
        }
      }

      @Override
      public void mutate(Progress<Integer, Integer, Object> c,
                         ExaminationEntry<Integer, Integer> e) {
        c.put(123);
      }

      @Override
      public void loaded(Progress<Integer, Integer, Object> c,
                         ExaminationEntry<Integer, Integer> e) {

      }
    };
    bc.execute(1, op);
    assertFalse(bc.containsKey(1));
    assertTrue(bc.getStatistics().getSize() == 0);
    bc.put(1, 987);
    bc.execute(1, op);
    assertEquals("unchanged", 987, (int) bc.peek(1));
  }

}
