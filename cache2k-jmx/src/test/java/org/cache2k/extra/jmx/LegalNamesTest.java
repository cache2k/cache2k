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

import org.cache2k.Cache;
import org.junit.jupiter.api.Test;

import javax.management.MBeanInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.extra.jmx.JmxSupportTest.getCacheInfo;

/**
 * Test legal characters in names.
 *
 * @author Jens Wilke
 */
public class LegalNamesTest {

  private static final String LEGAL_CHARACTERS =
    ",-()~_.+!'%#";

  @Test
  public void testCache() throws Exception {
    for (char aChar : LEGAL_CHARACTERS.toCharArray()) {
      String name = LegalNamesTest.class.getName() + "-test-with-char-" + aChar;
      Cache c = forUnknownTypes()
        .name(name)
        .enable(JmxSupport.class)
        .build();
      assertThat(c.getCacheManager().getName()).isEqualTo("default");
      assertThat(c.getCacheManager().isDefaultManager()).isTrue();
      assertThat(c.getName()).isEqualTo(name);
      MBeanInfo inf = getCacheInfo(c.getName());
      assertThat(inf).isNotNull();
      c.close();
    }
  }

}
