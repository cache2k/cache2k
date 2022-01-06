package org.cache2k.pinpoint.stress.pairwise;

/*-
 * #%L
 * cache2k pinpoint
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

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Experimental. Alternative version with lambdas.
 *
 * @author Jens Wilke
 */
public class Pairwise<A, B> {

  public static Pairwise<?, ?> empty() {
    return new Pairwise<>();
  }

  private Runnable setup;
  private Supplier<A> supplierA;
  private Supplier<B> supplierB;

  public Pairwise<A, B> setup(Runnable action) {
    setup = action;
    return this;
  }
  public <NewA> Pairwise<NewA, B> a(Supplier<NewA> supplier) {
    Pairwise<NewA, B> me = (Pairwise<NewA, B>) this;
    me.supplierA = supplier;
    return me;
  }
  public <NewB> Pairwise<A, NewB> b(Supplier<NewB> supplier) {
    Pairwise<A, NewB> me = (Pairwise<A, NewB>) this;
    return me;
  }
  public Pairwise<Void, B> a(Runnable action) {
    Pairwise<Void, B> me = (Pairwise<Void, B>) this;
    return me;
  }
  public Pairwise<A, Void> b(Runnable action) {
    Pairwise<A, Void> me = (Pairwise<A, Void>) this;
    return me;
  }
  public Pairwise<A, B> check(BiConsumer<A, B> consumer) {
    return this;
  }

}

class Test2 extends Pairwise<Object, Object> {

  AtomicInteger counter1 = new AtomicInteger();
  ConcurrentMap<Integer, Integer> map;
  Integer key;

  {
    setup(() -> {
      counter1.set(0);
      map.remove(key);
    })
    .a(() ->
      map.compute(key, (key, value) -> {
        counter1.getAndIncrement(); return value == null ? 1 : 11;
    }))
    .b(() ->
      map.putIfAbsent(key, 2)
    )
    .check((integer, integer2) -> { })
    .a(() -> { })
      .check((unused, b) -> { });
  }

}
