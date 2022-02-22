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

import org.cache2k.pinpoint.ExceptionCollector;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class ExceptionCollectorTest {

  @Test
  public void initial() {
    ExceptionCollector collector = new ExceptionCollector();
    collector.assertNoException();
    assertThat(collector.getFirstException()).isNull();
    assertThat(collector.getExceptionCount()).isEqualTo(0);
  }

  @Test
  public void oneException() {
    ExceptionCollector collector = new ExceptionCollector();
    ExpectedException exception = new ExpectedException();
    collector.runAndCatch(() -> { throw exception; } );
    assertThat(collector.getFirstException()).isSameAs(exception);
    assertThat(collector.getExceptionCount()).isEqualTo(1);
    assertThatCode(() -> collector.assertNoException())
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("No exception expected, 1 exceptions, first one propagated");
  }

  @Test
  public void twoExceptions() {
    ExceptionCollector collector = new ExceptionCollector();
    ExpectedException exception = new ExpectedException();
    ExpectedException otherException = new ExpectedException();
    collector.exception(exception);
    collector.exception(otherException);
    assertThat(collector.getFirstException()).isSameAs(exception);
    assertThat(collector.getExceptionCount()).isEqualTo(2);
    assertThatCode(() -> collector.assertNoException())
      .isInstanceOf(AssertionError.class)
      .hasMessageContaining("No exception expected, 2 exceptions, first one propagated");
  }

}
