package org.cache2k.core.log;

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

import org.cache2k.test.util.ExpectedException;
import org.junit.Test;
import org.slf4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test log abstraction log adapters. In module test-init is another test for
 * a SPI implementation of LogFactory
 *
 * @author Jens Wilke
 * @see Log
 */
public class LogTest {

  @Test
  public void julAdapter() {
    java.util.logging.Logger logger = java.util.logging.Logger.getLogger(this.getClass().getName());
    StringBuilder buf = new StringBuilder();
    logger.setFilter(record -> {
      buf.append(record.getLevel().getName()).append('=').append(record.getMessage()).append(',');
      return false;
    });
    Log log = new Log.JdkLogger(logger);
    assertThat(log.isDebugEnabled()).isFalse();
    assertThat(log.isInfoEnabled()).isTrue();
    Exception ex = new ExpectedException();
    log.debug("debug");
    log.debug("debug", ex);
    log.info("info");
    log.info("info", ex);
    log.warn("warn");
    log.warn("warn", ex);
    assertThat(buf.toString()).isEqualTo("INFO=info,INFO=info,WARNING=warn,WARNING=warn,");
  }

  @Test
  public void suppressionCounter() {
    Log.SuppressionCounter log = new Log.SuppressionCounter();
    assertThat(log.isDebugEnabled()).isTrue();
    assertThat(log.isInfoEnabled()).isTrue();
    Exception ex = new ExpectedException();
    log.debug("debug");
    log.debug("debug", ex);
    log.info("info");
    log.info("info", ex);
    log.warn("warn");
    log.warn("warn", ex);
    assertThat(log.getWarnCount()).isEqualTo(2);
    assertThat(log.getDebugCount()).isEqualTo(2);
    assertThat(log.getInfoCount()).isEqualTo(2);
  }

}
