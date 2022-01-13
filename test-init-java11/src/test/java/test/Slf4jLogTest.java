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

import org.cache2k.core.log.Log;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jens Wilke
 */
public class Slf4jLogTest {

  /**
   * Expect Slf4j is active
   */
  @Test
  public void usingSlf4j() {
    assertThat(Log.getLog("any").getClass()).isEqualTo(Log.Slf4jLogger.class);
  }

  /**
   * Test all relevant log methods via a simple proxy
   */
  @Test
  public void slf4jAdapter() {
    InvocationsRecorder recorder = new InvocationsRecorder();
    Log log = new Log.Slf4jLogger(recorder.getProxy(Logger.class));
    assertThat(log.isDebugEnabled()).isTrue();
    assertThat(log.isInfoEnabled()).isTrue();
    Exception ex = new IllegalMonitorStateException();
    log.debug("debug");
    log.debug("debug", ex);
    log.info("info");
    log.info("info", ex);
    log.warn("warn");
    log.warn("warn", ex);
    String exceptionString = ex.getClass().getName();
    assertThat(recorder.getRecording()).isEqualTo("isDebugEnabled()\n" +
      "isInfoEnabled()\n" +
      "debug(\"debug\")\n" +
      "debug(\"debug\"" + exceptionString + ")\n" +
      "info(\"info\")\n" +
      "info(\"info\")\n" +
      "warn(\"warn\")\n" +
      "warn(\"warn\"" + exceptionString + ")\n");
  }

  static class InvocationsRecorder {

    private final StringBuilder recording = new StringBuilder();

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T>... types) {
      InvocationHandler h = (proxy, method, args) -> {
        recording.append(method.getName());
        recording.append('(');
        if (args != null) {
          for (Object o : args) {
            if (o instanceof String) {
              recording.append('"').append(o).append('"');
            } else {
              recording.append(o);
            }
          }
        }
        recording.append(')');
        recording.append('\n');
        if (Boolean.class.equals(method.getReturnType()) ||
          Boolean.TYPE.equals(method.getReturnType())) {
          return Boolean.TRUE;
        } else if (method.getReturnType() != Void.TYPE) {
          throw new IllegalArgumentException("unsupported return type: " + method.getReturnType());
        }
        return null;
      };
      return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(), types, h);
    }

    public String getRecording() {
      return recording.toString();
    }

  }

}
