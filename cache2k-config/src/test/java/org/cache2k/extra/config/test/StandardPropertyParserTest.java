package org.cache2k.extra.config.test;

/*-
 * #%L
 * cache2k config file support
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

import org.cache2k.extra.config.generic.StandardPropertyParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.extra.config.generic.StandardPropertyParser.parseLongWithUnitSuffix;

/**
 * @author Jens Wilke
 */
public class StandardPropertyParserTest {

  @Test
  public void parseLong_123M() {
    assertThat(parseLongWithUnitSuffix("123M")).isEqualTo(123 * 1000 * 1000);
  }

  @Test
  public void parseLong_123() {
    assertThat(parseLongWithUnitSuffix("123")).isEqualTo(123);
  }

  @Test
  public void parseLong_123_5678() {
    assertThat(parseLongWithUnitSuffix("123_5678")).isEqualTo(1235678);
  }

  @Test
  public void parseLong_123xy() {
    assertThatCode(() -> {
      StandardPropertyParser.parseLongWithUnitSuffix("123xy");
    }).isInstanceOf(NumberFormatException.class);
  }

  @Test
  public void parseLong_xy() {
    assertThatCode(() -> {
      StandardPropertyParser.parseLongWithUnitSuffix("xy");
    }).isInstanceOf(NumberFormatException.class);
  }

}
