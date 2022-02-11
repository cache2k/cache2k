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

import org.cache2k.pinpoint.NeverExecutedError;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.assertj.core.api.Assertions.*;

/**
 * @author Jens Wilke
 */
public class ActorPairSuiteTest {

  @Test
  public void cleanRun() {
    AtomicInteger count = new AtomicInteger(47);
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(0)
      .addPair(new ActorPair<Object, Object>() {
        @Override
        public void setup() {
          count.set(10);
        }

        @Override
        public Object actor1() {
          count.decrementAndGet();
          return null;
        }

        @Override
        public Object actor2() {
          count.decrementAndGet();
          return null;
        }

        @Override
        public void observe() {
          count.decrementAndGet();
        }

        @Override
        public void check(Object o, Object o2) {
          assertThat(count.get()).isEqualTo(7);
        }
      });
    s.run();
  }

  @Test
  public void interrupted() {
    ActorPairSuite s = new ActorPairSuite()
      .addPair(new DefaultPair<>());
    Thread.currentThread().interrupt();
    s.run();
  }

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
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("setup test");
  }

  @Test
  public void exceptionOnSecondRun() {
    AtomicInteger count = new AtomicInteger();
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(Long.MAX_VALUE)
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void check(Object r1, Object r2) {
          assertThat(count.getAndIncrement()).isEqualTo(0);
        }
      });
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("expected: 0")
      .hasMessageContaining("but was: 1");
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
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("actor1");
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
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("actor2");
  }

  @Test
  public void exceptionPropagationFromCheck() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void check(Object r1, Object r2) {
          throw new RuntimeException("check");
        }
      });
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("check");
  }

  @Test
  public void exceptionPropagationFromObserver() {
    ActorPairSuite s = new ActorPairSuite()
      .stopAtFirstException(true)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void observe() {
          throw new RuntimeException("observer");
        }
      });
    assertThatCode(s::run)
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("observer");
  }

  @Test
  public void noExceptionOneshot() {
    AtomicBoolean wasRun = new AtomicBoolean();
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(0)
      .maxParallel(1234)
      .addPair(new DefaultPair<Object>() {
        @Override
        public void setup() {
          wasRun.set(true);
        }
      });
    s.run();
    assertThat(wasRun.get()).isTrue();
  }

  @Test
  public void alternativeExecutor() {
    Executor ex = newCachedThreadPool();
    AtomicBoolean used = new AtomicBoolean();
    ActorPairSuite s = new ActorPairSuite()
      .runMillis(0)
      .maxParallel(1234)
      .executor(command -> {
        used.set(true);
        ex.execute(command);
      })
      .addPair(new DefaultPair<>());
    s.run();
    assertThat(used.get()).isTrue();
  }

  @Test
  public void initExceptionNoActorPairs() {
    assertThatCode(() ->
      new ActorPairSuite().run()
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void initExceptionWrongMaxParallel() {
    assertThatCode(() ->
      new ActorPairSuite().addPair(new DefaultPair<>()).maxParallel(0).run()
    ).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void observerDetection() {
    assertThat(OneShotPairRunner.observerPresent(this.getClass())).isFalse();
    assertThat(OneShotPairRunner.observerPresent(DefaultPair.class)).isFalse();
    ActorPair withObserver = new DefaultPair() {
      @Override
      public void observe() { }
    };
    assertThat(OneShotPairRunner.observerPresent(withObserver.getClass())).isTrue();
  }

  @Test
  public void observerThrowsException() {
    assertThatCode(() -> new DefaultPair().observe())
      .isInstanceOf(NeverExecutedError.class);
  }

  static class DefaultPair<R> implements ActorPairSingleType<R> {
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
    public void check(R r1, R r2) { }
  }

}
