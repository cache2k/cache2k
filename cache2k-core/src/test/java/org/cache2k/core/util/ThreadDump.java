package org.cache2k.core.util;

/*
 * #%L
 * cache2k implementation
 * %%
 * Copyright (C) 2000 - 2018 headissue GmbH, Munich
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Thread dump generation, inspired from: http://crunchify.com/how-to-generate-java-thread-dump-programmatically/
 *
 * @author Jens Wilke; created: 2014-09-13
 */
public class ThreadDump {

  public static String generateThredDump() {
    final StringBuilder sb = new StringBuilder();
    final ThreadMXBean _threadMXBean = ManagementFactory.getThreadMXBean();
    final ThreadInfo[] _infos =
      _threadMXBean.getThreadInfo(_threadMXBean.getAllThreadIds(), Integer.MAX_VALUE);
    for (ThreadInfo _info : _infos) {
      sb.append("Thread \"");
      sb.append(_info.getThreadName());
      sb.append("\" ");
      final Thread.State _state = _info.getThreadState();
      sb.append(_state);
      final StackTraceElement[] stackTraceElements = _info.getStackTrace();
      for (final StackTraceElement stackTraceElement : stackTraceElements) {
        sb.append("\n    at ");
        sb.append(stackTraceElement);
      }
      sb.append("\n\n");
    }
    return sb.toString();
  }

}
