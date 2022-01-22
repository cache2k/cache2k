/*-
 * #%L
 * cache2k micrometer monitoring support
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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;

import static io.micrometer.core.instrument.Metrics.globalRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.extra.micrometer.Cache2kCacheMetrics.monitor;

import org.cache2k.extra.micrometer.MicrometerSupport;
import org.junit.jupiter.api.Test;

/**
 * Does test the various bind option.
 *
 * @author Jens Wilke
 */
public class MicroMeterTest {

  @Test
  public void programmaticBind() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes().build();
    monitor(registry, cache);
    assertThat(registry.get("cache.puts").functionCounter().count() >= 0).isTrue();
    cache.close();
  }

  /**
   * Tests a feature of micrometer. Registering twice has no effect.
   * Metrics registered first stay and subsequent are discarded.
   */
  @Test
  public void doubleBind() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes().build();
    monitor(registry, cache);
    Object counter = registry.get("cache.puts").functionCounter();
    monitor(registry, cache);
    assertThat(registry.get("cache.puts").functionCounter()).isSameAs(counter);
    cache.close();
  }

  @Test
  public void notBoundToGlobalRegistryWhenDisabled() {
    Cache cache = Cache2kBuilder.forUnknownTypes()
      .name("bindToGlobalRegistryWhenDisabled")
      .enable(MicrometerSupport.class)
      .disableMonitoring(true)
      .build();
    try {
      globalRegistry.get("cache.puts")
        .tag("cache", cache.getName()).meters();
      fail("exception expected");
    } catch (MeterNotFoundException expected) { }
  }

  @Test
  public void bindToGlobalRegistryWhenEnabled() {
    Cache cache = forUnknownTypes()
      .name("bindToGlobalRegistryWhenEnabled")
      .enable(MicrometerSupport.class)
      .disableMonitoring(false)
      .build();
    assertThat(globalRegistry.get("cache.puts")
      .tag("cache", cache.getName())
      .functionCounter().count() >= 0).isTrue();
  }

  @Test
  public void bindWhenStatisticsEnabled() {
    Cache cache = forUnknownTypes()
      .enable(MicrometerSupport.class)
      .disableStatistics(true)
      .name("bindWhenStatisticsDisabled")
      .build();
    assertThat(globalRegistry.get("cache.puts")
      .tag("cache", cache.getName())
      .functionCounter().count() >= 0).isTrue();
  }

  @Test
  public void bindToSpecificRegistry() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes()
      .enableWith(MicrometerSupport.class,
        b -> b.registry(registry))
      .build();
    try {
      globalRegistry.get("cache.puts").tag("cache", cache.getName()).meters();
      fail("not in global registry");
    } catch (MeterNotFoundException expected) { }
    assertThat(registry.get("cache.puts").
      tag("cache", cache.getName()).functionCounter().count() >= 0).isTrue();
  }

  @Test
  public void checkBasicMetrics() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes().build();
    monitor(registry, cache);
    cache.put(1, 1);
    cache.put(2, 1);
    cache.put(3, 1);
    cache.put(2, 1234);
    cache.get(1);
    cache.get(2);
    cache.get(1234);
    cache.remove(3);
    assertThat((int) registry.get("cache.puts").functionCounter().count()).isEqualTo(4);
    assertThat((int) registry.get("cache.size").gauge().value()).isEqualTo(2);
    assertThat((int) registry.get("cache.evictions").functionCounter().count()).isEqualTo(1);
    assertThat((int) registry.get("cache.gets")
      .tag("result", "hit").functionCounter().count()).isEqualTo(2);
    assertThat((int) registry.get("cache.gets")
      .tag("result", "miss").functionCounter().count()).isEqualTo(1);
    cache.close();
  }

  @Test
  public void checkLoaderMetrics() throws InterruptedException {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes()
      .loader(key -> key)
      .build();
    monitor(registry, cache);
    cache.get(1);
    cache.get(2);
    cache.get(3);
    cache.peek(4);
    cache.get(3);
    assertThat((int) registry.get("cache.puts").functionCounter().count()).isEqualTo(0);
    assertThat((int) registry.get("cache.size").gauge().value()).isEqualTo(3);
    assertThat((int) registry.get("cache.evictions").functionCounter().count()).isEqualTo(0);
    assertThat((int) registry.get("cache.gets")
      .tag("result", "hit").functionCounter().count()).isEqualTo(1);
    assertThat((int) registry.get("cache.gets")
      .tag("result", "miss").functionCounter().count()).isEqualTo(4);
    assertThat((int) registry.get("cache.load")
      .tag("result", "success").functionCounter().count()).isEqualTo(3);
    assertThat((int) registry.get("cache.load")
      .tag("result", "failure").functionCounter().count()).isEqualTo(0);
    assertThat(registry.get("cache.load.duration").gauge().value()).isEqualTo(0.0);
    cache.close();
  }

  @Test
  public void checkBasicMetricsWithDisabledStatistics() {
    MeterRegistry registry = new SimpleMeterRegistry();
    Cache cache = forUnknownTypes()
      .disableStatistics(true)
      .build();
    monitor(registry, cache);
    cache.put(1, 1);
    cache.put(2, 1);
    cache.put(3, 1);
    cache.put(2, 1234);
    cache.get(1);
    cache.get(2);
    cache.get(1234);
    cache.remove(3);
    assertThat((int) registry.get("cache.puts").functionCounter().count()).isEqualTo(0);
    assertThat((int) registry.get("cache.size").gauge().value()).isEqualTo(2);
    assertThat((int) registry.get("cache.gets")
      .tag("result", "hit").functionCounter().count()).isEqualTo(0);
    assertThat(registry.getMeters().stream()
      .map(meter -> meter.getId().getName()).noneMatch(s -> s.equals("cache.evictions")))
      .as("Meter cache.evictions not present")
      .isTrue();
    cache.close();
  }

}
