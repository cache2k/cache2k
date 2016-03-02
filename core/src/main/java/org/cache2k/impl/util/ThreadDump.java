package org.cache2k.impl.util;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
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

  private ThreadDump() {
  }

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
