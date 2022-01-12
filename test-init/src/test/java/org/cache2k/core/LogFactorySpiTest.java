package org.cache2k.core;

/*-
 * #%L
 * cache2k initialization tests
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

import org.cache2k.core.log.Log;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Check that LogFactory provider gets active if present
 *
 * @author Jens Wilke
 */
public class LogFactorySpiTest {

  @Test
  public void checkActive() {
    assertThat(Log.getLog("ignored")).isSameAs(new TestLogFactory().getLog("also ignored"));
  }

}
