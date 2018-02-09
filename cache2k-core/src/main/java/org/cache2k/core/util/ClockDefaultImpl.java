package org.cache2k.core.util;

/*
 * #%L
 * cache2k core
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

/**
 * @author Jens Wilke
 */
public class ClockDefaultImpl implements InternalClock {

  public final static ClockDefaultImpl INSTANCE = new ClockDefaultImpl();
  private final Object internal = new Object();

  @Override
  public long millis() {
    return System.currentTimeMillis();
  }

  @Override
  public void waitMillis(final long _millis) throws InterruptedException {
    Thread.sleep(_millis);
  }

  @Override
  public Notifier createNotifier() {
    return new MyNotifier();
  }

  @Override
  public void waitMillis(final Notifier n, final long _millis) throws InterruptedException {
    ((MyNotifier) n).internal.wait(_millis);
  }

  @Override
  public void runExclusive(final Notifier n, final Runnable r) {
    MyNotifier m = ((MyNotifier) n);
    synchronized (m.internal) {
      r.run();
    }
  }

  @Override
  public <T, R> R runExclusive(final Notifier n, T v, final ExclusiveFunction<T, R> r) {
    MyNotifier m = ((MyNotifier) n);
    synchronized (m.internal) {
      return r.apply(v);
    }
  }

  static class MyNotifier implements Notifier {

    final Object internal = new Object();

    @Override
    public void sendNotify() {
      internal.notifyAll();
    }

  }

}
