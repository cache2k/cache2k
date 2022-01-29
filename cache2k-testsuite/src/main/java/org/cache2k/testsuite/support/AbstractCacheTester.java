package org.cache2k.testsuite.support;

/*-
 * #%L
 * cache2k testsuite on public API
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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.ForwardingCache;
import org.cache2k.operation.CacheControl;
import org.cache2k.operation.TimeReference;
import org.cache2k.pinpoint.CaughtInterruptedExceptionError;
import org.cache2k.pinpoint.TimeBox;
import org.cache2k.pinpoint.TimeoutError;
import org.cache2k.pinpoint.UnexpectedExceptionError;
import org.junit.jupiter.api.AfterEach;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * @author Jens Wilke
 */
public class AbstractCacheTester<K, V> extends ForwardingCache<K, V>
  implements ExtendedCache<K, V>, CommonValues, SetupFragments {

  private TimeReference clock;
  private Cache<K, V> createdCache;
  /** Provides alternative cache interface, with out any decorations */
  public final Cache<K, V> cache = new ForwardingCache<K, V>() {
    @Override
    protected Cache<K, V> delegate() {
      if (createdCache == null) {
        throw new NullPointerException("Cache needs to be created");
      }
      return createdCache;
    }
  };
  public final DataType<K> keys;
  public final DataType<V> values;
  public final K k0;
  public final K k1;
  public final K k2;
  public final V v0;
  public final V v1;
  public final V v2;

  public AbstractCacheTester() {
    TestContext<K, V> ctx = provideTestContext();
    keys = ctx.getKeys();
    values = ctx.getValues();
    k0 = keys.getValue0();
    k1 = keys.getValue1();
    k2 = keys.getValue2();
    v0 = values.getValue0();
    v1 = values.getValue1();
    v2 = values.getValue2();
  }

  @SuppressWarnings("unchecked")
  protected TestContext<K, V> provideTestContext() {
    return (TestContext<K, V>) TestContext.DEFAULT;
  }

  protected Cache2kBuilder<K, V> provideBuilder() {
    return Cache2kBuilder.forUnknownTypes()
      .keyType(keys.getCacheType())
      .valueType(values.getCacheType());
  }

  @Override
  protected Cache<K, V> delegate() {
    return createdCache;
  }

  protected void init() {
    init(b -> {});
  }

  protected void init(Consumer<Cache2kBuilder<K, V>> consumer) {
    clock = TimeReference.DEFAULT;
    Cache2kBuilder<K, V> builder = provideBuilder();
    consumer.accept(builder);
    createdCache = builder.build();
  }

  public TimeReference clock() { return clock; }

  public long now() {
    return clock.ticks();
  }

  public void sleep(long ticks) {
    try {
      clock.sleep(ticks);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new CaughtInterruptedExceptionError();
    }
  }

  public TimeBox within(long ticks) {
    return new TimeBox(() -> clock.ticks(), ticks);
  }

  @AfterEach
  public void autoCleanUp() {
    createdCache.close();
  }

  public CacheControl control() {
    return CacheControl.of(this);
  }

  public void waitFor(CompletableFuture<?> future) {
    try {
      future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      throw new TimeoutError(TIMEOUT_MILLIS);
    } catch (InterruptedException e) {
      throw new CaughtInterruptedExceptionError();
    } catch (ExecutionException e) {
      throw new UnexpectedExceptionError(e);
    }
  }

}
