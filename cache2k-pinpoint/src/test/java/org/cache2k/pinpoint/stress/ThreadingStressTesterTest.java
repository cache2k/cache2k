package org.cache2k.pinpoint.stress;

/*-
 * #%L
 * cache2k pinpoint
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

import org.cache2k.pinpoint.CaughtInterruptedExceptionError;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class ThreadingStressTesterTest {

  @Test
  public void initial() {
    ThreadingStressTester tst = new ThreadingStressTester();
    assertThat(tst.getTestTimeMillis()).isEqualTo(1000);
    assertThat(tst.isDisablePrintThreadExceptions()).isFalse();
    long t0 = System.currentTimeMillis();
    tst.run();
    assertThat(tst.getStartTime()).isGreaterThanOrEqualTo(t0);
    assertThat(tst.getStopTime()).isGreaterThanOrEqualTo(t0);
  }

  @Test
  public void params() {
    ThreadingStressTester tst = new ThreadingStressTester();
    assertThat(tst.getTestTimeMillis()).isEqualTo(1000);
    tst.setTestTimeMillis(2000);
    assertThat(tst.getTestTimeMillis()).isEqualTo(2000);
    assertThat(tst.getErrorOutput()).isEqualTo(System.err);
    tst.setTimeoutAfterInterruptMillis(4711);
    assertThat(tst.getTimeoutAfterInterruptMillis()).isEqualTo(4711);
    tst.setOneShotTimeoutMillis(4711);
    assertThat(tst.getOneShotTimeoutMillis()).isEqualTo(4711);

  }

  @Test
  public void oneshot() {
    AtomicInteger count = new AtomicInteger();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setOneShotMode(true);
    tst.addTask(4, count::incrementAndGet);
    tst.run();
    assertThat(count.get()).isEqualTo(4);
  }

  @Test
  public void exception() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addTask(() -> { throw new ExpectedException(); });
    tst.setErrorOutput(new PrintStream(new ByteArrayOutputStream()));
    assertThatCode(tst::run)
      .getRootCause().isInstanceOf(ExpectedException.class);
  }

  @Test
  public void onError() {
    AtomicInteger count = new AtomicInteger();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addTask(() -> { throw new ExpectedException(); });
    tst.setDisablePrintThreadExceptions(true);
    tst.setOnError(count::incrementAndGet);
    assertThatCode(tst::run)
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  public void onFinal() {
    AtomicInteger count = new AtomicInteger();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setOneShotMode(true);
    tst.setFinalCheck(count::incrementAndGet);
    tst.addTask(() -> {});
    tst.run();
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  public void onFinalWithException() {
    AtomicInteger count = new AtomicInteger();
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setOneShotMode(true);
    tst.setFinalCheck(() -> { count.incrementAndGet(); throw new ExpectedException(); });
    tst.addTask(() -> {});
    assertThatCode(tst::run)
      .getRootCause().isInstanceOf(ExpectedException.class);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  public void interrupted() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.setDisablePrintThreadExceptions(true);
    tst.addExceptionalTask(() -> { throw new InterruptedException(); });
    assertThatCode(tst::run)
      .getRootCause().isInstanceOf(InterruptedException.class);
  }

  @Test
  public void anyException() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setDoNotInterrupt(true);
    tst.setDisablePrintThreadExceptions(true);
    tst.addExceptionalTask(() -> { throw new IOException(); });
    assertThatCode(tst::run)
      .getRootCause().isInstanceOf(IOException.class);
  }

  @Test
  public void testTime0AndInterrupt() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addExceptionalTask(() -> Thread.sleep(99999999));
    tst.setTestTimeMillis(0);
    tst.run();
  }

  @Test
  public void testTime0WithTimeout() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setErrorOutput(new PrintStream(new ByteArrayOutputStream()));
    tst.addExceptionalTask(() -> {
      try {
        Thread.sleep(1234);
      } catch (InterruptedException ignore) { }
      Thread.sleep(1234);
    });
    tst.setTimeoutAfterInterruptMillis(0);
    tst.setTestTimeMillis(0);
    assertThatCode(tst::run)
      .isInstanceOf(ThreadingStressTester.TimeoutThreadingStressTestException.class);
  }

  @Test
  public void oneShotTimeout() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.addExceptionalTask(() -> Thread.sleep(99999999));
    tst.setOneShotMode(true);
    tst.setOneShotTimeoutMillis(0);
    tst.setErrorOutput(new PrintStream(new ByteArrayOutputStream()));
    assertThatCode(tst::run)
      .isInstanceOf(ThreadingStressTester.TimeoutThreadingStressTestException.class);
  }

  @Test
  public void mainThreadInterrupted() {
    Thread.currentThread().interrupt();
    ThreadingStressTester tst = new ThreadingStressTester();
    assertThatCode(tst::run)
      .isInstanceOf(CaughtInterruptedExceptionError.class);
  }

  @SuppressWarnings("StatementWithEmptyBody")
  @Test
  public void busyLoop() {
    ThreadingStressTester tst = new ThreadingStressTester();
    tst.setTestTimeMillis(0);
    tst.addTask(() -> { while(!tst.isShouldStop()) { } });
    tst.run();
  }

}
