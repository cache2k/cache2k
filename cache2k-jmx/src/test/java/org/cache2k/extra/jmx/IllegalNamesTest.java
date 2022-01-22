package org.cache2k.extra.jmx;

/*-
 * #%L
 * cache2k JMX support
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

import org.cache2k.CacheManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;

/**
 * Test illegal characters in names.
 *
 * @author Jens Wilke
 */
public class IllegalNamesTest {

  @ParameterizedTest
  @ValueSource(chars = {'"', '*', '{', '}', '[', ']' , ':', '=', '\\'})
  public void testCache(char illegalChar) {
    assertThatCode(() ->{
      forUnknownTypes()
        .name(IllegalNamesTest.class.getName() + "-test-with-char-" + illegalChar)
        .build();
    }).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(chars = {'"', '*', '{', '}', '[', ']' , ':', '=', '\\'})
  public void testManager(char illegalChar) {
    assertThatCode(() ->{
      CacheManager.getInstance(IllegalNamesTest.class.getName() + "-char-" + illegalChar);
    }).isInstanceOf(IllegalArgumentException.class);
  }

}
