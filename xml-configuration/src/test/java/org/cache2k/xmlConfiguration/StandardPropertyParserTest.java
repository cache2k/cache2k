package org.cache2k.xmlConfiguration;

/*
 * #%L
 * cache2k XML configuration
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.junit.FastTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 * @author Jens Wilke
 */
@Category(FastTests.class)
public class StandardPropertyParserTest {

  @Test
  public void parseLong_123MB() {
    assertEquals(123 * 1000 * 1000, StandardPropertyParser.parseLongWithUnitSuffix("123MB"));
  }

  @Test
  public void parseLong_123() {
    assertEquals(123, StandardPropertyParser.parseLongWithUnitSuffix("123"));
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
