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

/**
 * @author Jens Wilke
 */
public class TimeoutErrorTest {

  @Test
  public void withNanos() {
    assertThat(new TimeoutError(Duration.ofNanos(123)).toString()).contains("0 seconds");
  }

  @Test
  public void withSeconds() {
    assertThat(new TimeoutError(Duration.ofSeconds(123)).toString()).contains("123 seconds");
  }

  @Test
  public void withMinutes() {
    assertThat(new TimeoutError(Duration.ofSeconds(120)).toString()).contains("2 minutes");
  }

}
