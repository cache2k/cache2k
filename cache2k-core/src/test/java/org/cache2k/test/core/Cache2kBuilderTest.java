package org.cache2k.test.core;

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

import org.cache2k.Cache;
import org.cache2k.Cache2kBuilder;
import org.cache2k.CacheManager;
import org.cache2k.config.Cache2kConfig;
import org.cache2k.config.CacheTypeCapture;
import org.cache2k.core.api.InternalCache;
import org.cache2k.core.log.Log;
import org.cache2k.testing.SimulatedClock;
import org.cache2k.testing.category.FastTests;

import static java.lang.Long.MAX_VALUE;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.cache2k.Cache2kBuilder.forUnknownTypes;
import static org.cache2k.CacheManager.getInstance;
import static org.cache2k.core.DefaultExceptionPropagator.SINGLETON;
import static org.cache2k.operation.CacheControl.of;
import static org.cache2k.test.core.Cache2kBuilderTest.BuildCacheInClassConstructor0.cache;
import static org.cache2k.test.core.StaticUtil.*;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cache builder tests for some special variants.
 *
 * @see Cache2kBuilder
 */
@Category(FastTests.class)
public class Cache2kBuilderTest {

  private static final String CLASSNAME = Cache2kBuilderTest.class.getName();

  /**
   * The method could return a raw type {@code <?, ?>} or <@code <Object, Object>}
   * Only the last options works well for all scenarios.
   * Version 1.x used a raw type. This does not work for configuration sections,
   * because we use the generics support then.
   */
  @Test
  public void forUnknownTypes_genericTyping() {
    Cache<Object, Object> cache = Cache2kBuilder.forUnknownTypes()
      .timeReference(new SimulatedClock())
      .loader(key -> key)
      .build();
    cache.put(123, 555);
    cache.put("123", "314");
    Cache raw = cache;
    raw.close();
  }

  @Test
  public void fromConfigBean() {
    Cache<Integer, Integer> cache =
      new Cache2kConfig<Integer, Integer>().builder().build();
    assertThat(of(cache).getKeyType().getTypeName()).isEqualTo("Object");
    cache.close();
  }

  @Test
  public void managerName() {
    Cache c = forUnknownTypes().eternal(true).build();
    assertThat(c.getCacheManager().getName()).isEqualTo("default");
    c.close();
  }

  @Test
  public void autoGeneratedCacheName() {
    Cache c1 = forUnknownTypes().eternal(true).build();
    assertThat(c1.getName().startsWith("_org.cache2k")).isTrue();
    Cache c2 = forUnknownTypes().eternal(true).build();
    assertThat(c1 != c2).isTrue();
    c1.close();
    c2.close();
  }

  @Test
  public void typesParameters() {
    Cache<Long, String> c =
      forUnknownTypes()
        .valueType(String.class)
        .keyType(Long.class)
        .eternal(true)
        .build();
    assertThat(((InternalCache<Long, String>) c).getKeyType().getType()).isEqualTo(Long.class);
    assertThat(((InternalCache<Long, String>) c).getValueType().getType()).isEqualTo(String.class);
    c.close();
  }

  @Test
  public void noTypes() {
    Cache c =
      Cache2kBuilder.forUnknownTypes().eternal(true).build();
    c.put("hallo", 234);
    c.close();
  }

  /**
   * If types are unknown you can cast to every generic type.
   */
  @Test
  public void noTypesCastObjectObject() {
    Cache<Object, Object> c = Cache2kBuilder.forUnknownTypes().eternal(true).build();
    c.put("hallo", 234);
    c.close();
  }

  /**
   * If types are unknown you can cast to every generic type.
   */
  @Test
  public void noTypesCastStringInt() {
    Cache<String, Integer> c = (Cache<String, Integer>) (Cache)
      Cache2kBuilder.forUnknownTypes().eternal(true).build();
    c.put("hallo", 234);
    c.close();
  }

  @Test
  public void noTypesAnyCache() {
    Cache<?, ?> c = Cache2kBuilder.forUnknownTypes().eternal(true).build();
    c.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void anonymousWithoutTypes() {
    Cache c = new Cache2kBuilder(){}.eternal(true).build();
    c.clear();
  }

  @Test(expected = IllegalArgumentException.class)
  public void anonymousWithObjectObject() {
    Cache c = new Cache2kBuilder<Object, Object>() {
    }.eternal(true).build();
    c.clear();
  }

  @Test
  public void collectionValueType() {
    Cache<Long, List<String>> c =
      new Cache2kBuilder<Long, List<String>>() {}
        .eternal(true)
        .build();
    c.close();
  }

  /**
   * When using classes for the type information, there is no generic type information.
   * There is an ugly cast to (Object) needed to strip the result and be able to cast
   * to the correct one.
   */
  @Test
  public void collectionValueClass() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>) (Object) Cache2kBuilder.of(Long.class, List.class).eternal(true).build();
    c.put(123L, new ArrayList<>());
    c.close();
  }

  /**
   * Use the cache type to specify a type with generic parameter. No additional cast is needed.
   */
  @Test
  public void collectionValueCacheType() {
    Cache<Long, List<String>> c =
      Cache2kBuilder.forUnknownTypes()
        .keyType(Long.class)
        .valueType(new CacheTypeCapture<List<String>>() {})
        .eternal(true)
        .build();
    c.put(123L, new ArrayList<>());
    c.close();
  }

  @Test
  public void typesParametersWithList() {
    Cache<Long, List> c =
      Cache2kBuilder.forUnknownTypes()
        .valueType(List.class)
        .keyType(Long.class)
        .eternal(true)
        .build();
    c.close();
  }

  @Test
  public void noTypesAndCast() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>) (Cache)
        Cache2kBuilder.forUnknownTypes()
          .eternal(true)
          .build();
    c.close();
  }

  @Test(expected = IllegalArgumentException.class)
  public void arrayKeyYieldsException() {
    new Cache2kBuilder<Integer[], String>() {}.build();
  }

  @Test(expected = IllegalArgumentException.class)
  public void arrayValueYieldsException() {
    new Cache2kBuilder<String, Integer[]>() {}.build();
  }

  @Test
  public void cacheNameForAnnotationDefault() {
    Cache<Long, List<String>> c =
      (Cache<Long, List<String>>) (Cache)
        Cache2kBuilder.forUnknownTypes()
          .eternal(true)
          .name("package.name.ClassName.methodName(package.ParameterType,package.ParameterType")
          .build();
    c.close();
  }

  @Test
  public void cacheNameInConstructor0() {
    Cache c = new BuildCacheInConstructor0().cache;
    assertThat(c.getName()).startsWith("_" + CLASSNAME + "$BuildCacheInConstructor0.INIT");
    c.close();
  }

  @Test
  public void cacheNameInConstructor1() {
    Cache c = new BuildCacheInConstructor1().cache;
    assertThat(c.getName()).startsWith("_" + CLASSNAME + "$BuildCacheInConstructor1.INIT");
    c.close();
  }

  @Test
  public void cacheNameInConstructor2() {
    Cache c = new BuildCacheInConstructor2().cache;
    assertThat(c.getName()).startsWith("_" + CLASSNAME + "$BuildCacheInConstructor2.INIT");
    c.close();
  }

  @Test
  public void cacheNameInClassConstructor0() {
    Cache c = cache;
    assertThat(c.getName()).startsWith("_" + CLASSNAME + "$BuildCacheInClassConstructor0.CLINIT");
    c.close();
  }

  @Test
  public void duplicateCacheName() {
    String managerName = getClass().getName() + ".duplicateCacheName";
    Log.registerSuppression(CacheManager.class.getName() + ":" + managerName, new Log.SuppressionCounter());
    CacheManager mgr = CacheManager.getInstance(managerName);
    try {
      Cache c0 = forUnknownTypes()
        .manager(mgr)
        .eternal(true)
        .name(this.getClass(), "same")
        .build();
      Cache c1 = forUnknownTypes()
        .manager(mgr)
        .eternal(true)
        .name(this.getClass(), "same")
        .build();
      fail("exception expected");
    } catch (IllegalStateException ex) {

    }
    mgr.close();
  }

  @Test(expected = IllegalStateException.class)
  public void managerAfterOtherStuff() {
    Cache2kBuilder.forUnknownTypes()
      .eternal(true)
      .manager(CacheManager.getInstance())
      .build();
  }

  @Test
  public void cacheCapacity10() {
    Cache c0 = forUnknownTypes()
      .entryCapacity(10)
      .build();
    assertThat(latestInfo(c0).getHeapCapacity()).isEqualTo(10);
    c0.close();
  }

  static final String ILLEGAL_CHARACTERS_IN_NAME =
    "{}|\\^&=\";:<>*?/" +
      ((char) 27) +
      ((char) 127) +
      ((char) 128) +
      "äßà\ufefe";

  @Test
  public void illegalCharacterInCacheName_unsafeSet() {
    for (char c : ILLEGAL_CHARACTERS_IN_NAME.toCharArray()) {
      try {
        Cache cache = forUnknownTypes()
          .name("illegalCharName" + c)
          .build();
        cache.close();
        fail("Expect exception for illegal name in character '" + c + "', code " + ((int) c));
      } catch (IllegalArgumentException ex) {
      }
    }
  }

  @Test
  public void legalCharacterInCacheName() {
    String legalChars = ".~,@ ()";
    legalChars += "$-_abcABC0123";
    Cache c = Cache2kBuilder.forUnknownTypes()
      .name(legalChars)
      .build();
    c.close();
  }

  @Test
  public void cacheCapacityDefault1802() {
    Cache c0 = forUnknownTypes().build();
    assertThat(latestInfo(c0).getHeapCapacity()).isEqualTo(1802);
    c0.close();
  }

  /**
   * Check that long is getting through completely.
   */
  @Test
  public void cacheCapacityUnlimitedLongMaxValue() {
    Cache c0 = forUnknownTypes()
      .entryCapacity(MAX_VALUE)
      .build();
    assertThat(latestInfo(c0).getHeapCapacity()).isEqualTo(MAX_VALUE);
    c0.close();
  }

  @Test
  public void cacheRemovedAfterClose() {
    String NAME = this.getClass().getSimpleName() + "-cacheRemovedAfterClose";
    CacheManager cm = getInstance(NAME);
    Cache c = forUnknownTypes()
      .manager(cm)
      .name(NAME)
      .build();
    assertThat(cm.getActiveCaches().iterator().next()).isEqualTo(c);
    c.close();
    assertThat(cm.getActiveCaches().iterator().hasNext()).isFalse();
  }

  @Test
  public void cacheRemovedAfterClose_WiredCache() {
    String NAME = this.getClass().getSimpleName() + "-cacheRemovedAfterCloseWiredCache";
    CacheManager cm = getInstance(NAME);
    Cache2kBuilder builder = forUnknownTypes()
      .manager(cm)
      .name(NAME);
    enforceWiredCache(builder);
    Cache c = builder.build();
    assertThat(cm.getActiveCaches().iterator().next()).isEqualTo(c);
    c.close();
    assertThat(cm.getActiveCaches().iterator().hasNext()).isFalse();
  }

  private void cacheClosedEventFired(boolean _wiredCache) {
    AtomicBoolean FIRED = new AtomicBoolean();
    Cache2kBuilder builder = forUnknownTypes();
    builder = builder.addCacheClosedListener(cache -> {
      FIRED.set(true);
      return completedFuture(null);
    });
    if (_wiredCache) {
     enforceWiredCache(builder);
    }
    Cache c = builder.build();
    c.close();
    assertThat(FIRED.get()).isTrue();
  }

  @Test
  public void cacheClosedEventFired() {
    cacheClosedEventFired(false);
  }

  @Test
  public void cacheClosedEventFired_WiredCache() {
    cacheClosedEventFired(true);
  }

  @Test
  public void cacheNameUnique() {
    Cache2kBuilder builder = forUnknownTypes();
    builder.name("hello", this.getClass(), "field");
    assertThat(builder.config().getName()).isEqualTo("hello~org.cache2k.test.core.Cache2kBuilderTest.field");
  }

  @Test
  public void cacheNameUniqueNull() {
    Cache2kBuilder builder = forUnknownTypes();
    builder.name(null, this.getClass(), "field");
    assertThat(builder.config().getName()).isEqualTo("org.cache2k.test.core.Cache2kBuilderTest.field");
  }

  @Test(expected = NullPointerException.class)
  public void cacheNameException() {
    Cache2kBuilder builder = Cache2kBuilder.forUnknownTypes();
    builder.name(this.getClass(), null);
  }

  @Test
  public void set_ExceptionPropagator() {
    Cache2kBuilder builder = forUnknownTypes();
    builder.exceptionPropagator(SINGLETON);
    assertThat(builder.config().getExceptionPropagator()).isNotNull();
  }

  @Test
  public void set_storeByReference() {
    Cache2kBuilder builder = forUnknownTypes();
    builder.storeByReference(true);
    assertThat(builder.config().isStoreByReference()).isTrue();
  }

  /** No loader present */
  @Test(expected = IllegalArgumentException.class)
  public void refreshAheadButNoLoader() {
    Cache c = Cache2kBuilder.forUnknownTypes()
      .refreshAhead(true)
      .expireAfterWrite(10, TimeUnit.SECONDS)
      .build();
    c.close();
  }

  /** No expiry time configured. Refresh could still be used with setExpiry */
  @Test
  public void refreshAheadButNoExpiry() {
    Cache c = Cache2kBuilder.forUnknownTypes()
      .loader(key -> null)
      .refreshAhead(true).build();
    c.close();
  }

  static class BuildCacheInConstructor0 {
    Cache<?,?> cache = Cache2kBuilder.forUnknownTypes().eternal(true).build();
  }

  static class BuildCacheInConstructor1 {

    Cache<?,?> cache;

    {
      cache = Cache2kBuilder.forUnknownTypes().eternal(true).build();
    }
  }

  static class BuildCacheInConstructor2 {
    Cache<?,?> cache;
    BuildCacheInConstructor2() {
      cache = Cache2kBuilder.forUnknownTypes().eternal(true).build();
    }
  }

  static class BuildCacheInClassConstructor0 {
    static Cache<?,?> cache =
      Cache2kBuilder.forUnknownTypes().eternal(true).build();
  }

}
