package org.cache2k.core;

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

import org.assertj.core.api.Assertions;
import org.cache2k.CacheException;
import org.cache2k.core.util.Util;
import org.cache2k.pinpoint.ExpectedException;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * @author Jens Wilke
 */
public class UtilTest {

  @Test
  public void waitFor() {
    CompletableFuture<Void> uncompleted = new CompletableFuture<>();
    Thread.currentThread().interrupt();
    Util.waitFor(uncompleted);
    assertThat(Thread.interrupted());
    CompletableFuture<Void> completeExceptional = new CompletableFuture<>();
    completeExceptional.completeExceptionally(new ExpectedException());
    assertThatCode(() -> Util.waitFor(completeExceptional))
      .isInstanceOf(CacheException.class)
      .getRootCause().isInstanceOf(ExpectedException.class);
    CompletableFuture<Void> completed = new CompletableFuture<>();
    completed.complete(null);
    Util.waitFor(completed);
  }
}
