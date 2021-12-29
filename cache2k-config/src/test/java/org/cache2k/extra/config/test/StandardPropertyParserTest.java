package org.cache2k.extra.config.test;

/*-
 * #%L
 * cache2k config file support
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

import org.cache2k.extra.config.generic.StandardPropertyParser;
import org.cache2k.testing.category.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class StandardPropertyParserTest {

  @Test
  public void parseLong_123M() {
    assertEquals(123 * 1000 * 1000, StandardPropertyParser.parseLongWithUnitSuffix("123M"));
  }

  @Test
  public void parseLong_123() {
    assertEquals(123, StandardPropertyParser.parseLongWithUnitSuffix("123"));
  }

  @Test
  public void parseLong_123_5678() {
    assertEquals(1235678, StandardPropertyParser.parseLongWithUnitSuffix("123_5678"));
  }

  @Test(expected = NumberFormatException.class)
  public void parseLong_123xy() {
    StandardPropertyParser.parseLongWithUnitSuffix("123xy");
  }

  @Test(expected = NumberFormatException.class)
  public void parseLong_xy() {
    StandardPropertyParser.parseLongWithUnitSuffix("xy");
  }

}
