package org.cache2k.jcache.provider;

/*-
 * #%L
 * cache2k JCache provider
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

import org.cache2k.jcache.provider.tckGlue.TckMBeanServerBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Jens Wilke
 */
public class TckMBeanServerBuilderTest {

  /**
   * For test coverage
   */
  @Test
  public void test() {
    TckMBeanServerBuilder.WrapperMBeanServerDelegate v =
      new TckMBeanServerBuilder.WrapperMBeanServerDelegate();
    assertNotNull(v.getMBeanServerId());
    assertNotNull(v.getImplementationVendor());
    assertNotNull(v.getImplementationVersion());
  }

}
