package org.cache2k.pinpoint.pairwise;

/*
 * #%L
 * cache2k pinpoint
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

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
public class ActorPairSuiteTest {

  @Test
  public void exceptionPropagationFromSetup() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void setup() {
          throw new RuntimeException("setup test");
        }
      });
    try {
      s.run();
      fail("exception expected");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("setup test"));
    }
  }

  @Test
  public void exceptionOnSecondRun() {
    final AtomicInteger count = new AtomicInteger();
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(Long.MAX_VALUE)
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void check(final Object r1, final Object r2) {
          assertEquals(0, count.getAndIncrement());
        }
      });
    try {
      s.run();
      fail("exception expected");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("java.lang.AssertionError: expected:<0> but was:<1>"));
    }
  }

  @Test
  public void exceptionPropagationFromActor1() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public Object actor1() {
          throw new RuntimeException("actor1");
        }
      });
    try {
      s.run();
      fail("exception expected");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("actor1"));
    }
  }

  @Test
  public void exceptionPropagationFromActor2() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public Object actor2() {
          throw new RuntimeException("actor2");
        }
      });
    try {
      s.run();
      fail("exception expected");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("actor2"));
    }
  }

  @Test
  public void exceptionPropagationFromCheck() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void check(final Object r1, final Object r2) {
          throw new RuntimeException("check");
        }
      });
    try {
      s.run();
      fail("exception expected");
    } catch (AssertionError ex) {
      assertTrue(ex.toString().contains("check"));
    }
  }

  @Test
  public void noExceptionOneshot() {
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(0)
      .maxParallel(1234)
      .addPair(new DefaultPair<Object>());
    s.run();
  }

  @Test
  public void alternativeExecutor() {
    final Executor ex = Executors.newCachedThreadPool();
    final AtomicBoolean used = new AtomicBoolean();
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(0)
      .maxParallel(1234)
      .executor(new Executor() {
        @Override
        public void execute(final Runnable command) {
          used.set(true);
          ex.execute(command);
        }
      })
      .addPair(new DefaultPair<Object>());
    s.run();
    assertTrue(used.get());
  }

  @Test(expected = IllegalArgumentException.class)
  public void initExceptionNoActorPairs() {
    new ActorPairSuite().run();
  }

  @Test(expected = IllegalArgumentException.class)
  public void initExceptionWrongMaxParallel() {
    new ActorPairSuite().addPair(new DefaultPair<Object>()).maxParallel(0).run();
  }

  static class DefaultPair<R> implements ActorPair<R> {
    @Override
    public void setup() { }

    @Override
    public R actor1() {
      return null;
    }

    @Override
    public R actor2() {
      return null;
    }

    @Override
    public void check(final R r1, final R r2) { }
  }

}
