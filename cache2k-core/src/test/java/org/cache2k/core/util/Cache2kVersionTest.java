package org.cache2k.core.util;

/*-
 * #%L
 * cache2k core implementation
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

import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.core.log.Log.*;
import static org.cache2k.core.util.Cache2kVersion.*;

/**
 * @author Jens Wilke; created: 2015-06-11
 */
@Category(FastTests.class)
public class Cache2kVersionTest {

  @Test
  public void testVersion() {
    String v = getVersion();
    assertThat(v).isNotNull();
    assertThat(v).isNotEqualTo("<unknown>");
    assertThat(v.equals("${version}") ||
      v.matches("[0-9]+\\..*"))
      .as("Either version is not replaced or something that looks like a version")
      .isTrue();
  }

  @Test
  public void skipVariables() {
    assertThat(isDefined("${version}")).isFalse();
  }

  @Test
  public void exception() {
    SuppressionCounter l = new SuppressionCounter();
    registerSuppression(Cache2kVersion.class.getName(), l);
    parse(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("ouch");
      }
    });
    assertThat(l.getWarnCount() > 0)
      .as("a warning")
      .isTrue();
    deregisterSuppression(Cache2kVersion.class.getName());
  }

}
