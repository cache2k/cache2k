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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class TaskSuccessGuardianTest {

  @Test
  public void immediateTimeout() {
    TaskSuccessGuardian guard = new TaskSuccessGuardian(Duration.ZERO);
    assertThatCode(() -> guard.awaitCompletionAndAssertSuccess()).isInstanceOf(TimeoutError.class);
  }

  @Test
  public void expectException() {
    TaskSuccessGuardian guard = new TaskSuccessGuardian();
    guard.executeGuarded(() -> { throw new ExpectedException(); });
    assertThatCode(() -> guard.awaitCompletionAndAssertSuccess())
      .isInstanceOf(AssertionError.class);
  }

  @Test
  public void expectSuccess() {
    TaskSuccessGuardian guard = new TaskSuccessGuardian();
    AtomicBoolean executed = new AtomicBoolean();
    guard.executeGuarded(() -> { executed.set(true); });
    guard.awaitCompletionAndAssertSuccess();
    assertThat(executed).isTrue();
  }

}
