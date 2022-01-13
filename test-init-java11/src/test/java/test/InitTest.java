package test;

/*-
 * #%L
 * cache2k initialization tests Java 11
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

import org.cache2k.Cache2kBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;

import org.cache2k.core.log.Log;
import org.junit.jupiter.api.Test;

/**
 * @author Jens Wilke
 */
public class InitTest {

  @Test
  public void config() {
    Cache2kBuilder<?, ?> b = forUnknownTypes();
    b.name("testCache");
    b.build();
    assertThat(b.config().getEntryCapacity()).isEqualTo(1802);
  }

  @Test
  public void slf4jInUse() {
    Log l = Log.getLog(InitTest.class);
    assertThat(l).isInstanceOf(Log.Slf4jLogger.class);
  }

}
