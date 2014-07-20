package org.cache2k.impl.threading;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory which names the threads uniquely.
 *
 * @author Jens Wilke; created: 2014-06-10
 */
public class GlobalThreadFactory implements ThreadFactory {

  AtomicInteger threadCount = new AtomicInteger();
  String prefix = "cache2k#";

  public GlobalThreadFactory(String _threadNamePrefix) {
    if (_threadNamePrefix != null) {
      this.prefix = _threadNamePrefix;
    }
  }

  @Override
  public Thread newThread(Runnable r) {
    int id = threadCount.getAndIncrement();
    Thread thr = new Thread(r);
    thr.setName(prefix + Integer.toString(id, 36));
    thr.setDaemon(true);
    return thr;
  }

}
