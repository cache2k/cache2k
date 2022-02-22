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

import java.time.Duration;

/**
 * Test aborted due to timeout
 *
 * @author Jens Wilke
 */
public class TimeoutError extends AssertionError {

  public TimeoutError(Duration timeout) {
    this(null, timeout);
  }

  public TimeoutError(String description, Duration timeout) {
    super(message(description, timeout));
  }

  static String message(String description, Duration timeout) {
    if (description != null) {
      return description + ". Timeout after " + readableDuration(timeout);
    }
    return "Timeout after " + readableDuration(timeout);
  }

  static String readableDuration(Duration timeout) {
    long seconds = timeout.getSeconds();
    if (seconds % 60 == 0 & seconds != 0) {
      return (seconds / 60) + " minutes";
    }
    return seconds + " seconds";
  }

}
