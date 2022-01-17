package org.cache2k.pinpoint;

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

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
@SuppressWarnings("ConstantConditions")
public class TimeBoxTest {

  long ticks = 1000;
  TimeBox tb;

  @SuppressWarnings("SameParameterValue")
  void setup(long timeBoxTicks) {
    tb = new TimeBox(() -> ticks, timeBoxTicks);
  }

  /**
   * TimeoutError thrown directly
   */
  @Test
  void timeout() {
    setup(100);
    assertThatCode(() -> tb.expectMaybe(() -> {throw new TimeoutError(Duration.ZERO); }))
      .isInstanceOf(TimeoutError.class);
  }

  @Test
  void propagateAssertError() {
    setup(100);
    assertThatCode(() -> tb
      .perform(() -> {})
      .expectMaybe(() -> assertThat(false).isTrue()))
      .isInstanceOf(AssertionError.class);
  }

  @Test
  void propagateAssertErrorInChain() {
    setup(100);
    assertThatCode(() -> tb
      .perform(() -> {})
      .expectMaybe(() -> {})
      .within(100)
      .expectMaybe(() -> assertThat(false).isTrue())
    ).isInstanceOf(AssertionError.class);
  }

  @Test
  void suppressAssertError() {
    setup(100);
    ticks += 100;
    tb.expectMaybe(() -> assertThat(false).isTrue());
  }

  /**
   * After missing the time box, don't execute / assert anything
   */
  @Test
  void suppressAssertErrorInChain() {
    setup(100);
    ticks += 100;
    tb.expectMaybe(() -> assertThat(false).isTrue())
      .within(100)
      .perform(() -> { throw new NeverExecutedError(); } )
      .expectMaybe(() -> { throw new NeverExecutedError(); } )
      .within(100)
      .perform(() -> { throw new NeverExecutedError(); } )
      .expectMaybe(() -> { throw new NeverExecutedError(); } );
  }

}
