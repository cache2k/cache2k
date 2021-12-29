package org.cache2k.core.util;

/*-
 * #%L
 * cache2k core implementation
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke; created: 2015-06-11
 */
@Category(FastTests.class)
public class Cache2kVersionTest {

  @Test
  public void testVersion() {
    String v = Cache2kVersion.getVersion();
    assertNotNull(v);
    assertNotEquals("<unknown>", v);
    assertTrue("Either version is not replaced or something that looks like a version",
        v.equals("${version}") ||
        v.matches("[0-9]+\\..*"));
  }

  @Test
  public void skipVariables() {
    assertFalse(Cache2kVersion.isDefined("${version}"));
  }

  @Test
  public void exception() {
    Log.SuppressionCounter l = new Log.SuppressionCounter();
    Log.registerSuppression(Cache2kVersion.class.getName(), l);
    Cache2kVersion.parse(new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("ouch");
      }
    });
    assertTrue("a warning", l.getWarnCount() > 0);
    Log.deregisterSuppression(Cache2kVersion.class.getName());
  }

}
