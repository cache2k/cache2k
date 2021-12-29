package org.cache2k.pinpoint;

/*-
 * #%L
 * cache2k pinpoint
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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/**
 * Generate a thread dump covering the whole VM. Useful for debugging.
 * Inspired from: http://crunchify.com/how-to-generate-java-thread-dump-programmatically/
 *
 * @author Jens Wilke
 */
public class VmThreadDump {

  public static String generateThreadDump() {
    StringBuilder sb = new StringBuilder();
    ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    ThreadInfo[] infos =
      threadMXBean.getThreadInfo(threadMXBean.getAllThreadIds(), Integer.MAX_VALUE);
    for (ThreadInfo info : infos) {
      sb.append("Thread \"");
      sb.append(info.getThreadName());
      sb.append("\" ");
      Thread.State _state = info.getThreadState();
      sb.append(_state);
      StackTraceElement[] stackTraceElements = info.getStackTrace();
      for (StackTraceElement stackTraceElement : stackTraceElements) {
        sb.append("\n    at ");
        sb.append(stackTraceElement);
      }
      sb.append("\n\n");
    }
    return sb.toString();
  }

}
