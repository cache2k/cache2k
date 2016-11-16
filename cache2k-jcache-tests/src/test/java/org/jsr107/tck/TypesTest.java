package org.jsr107.tck;

import domain.Beagle;
import domain.BorderCollie;
import domain.Chihuahua;
import domain.Dachshund;
import domain.Dog;
import domain.Hound;
import domain.Identifier;
import domain.Identifier2;
import domain.Papillon;
import domain.RoughCoatedCollie;
import junit.framework.Assert;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.configuration.OptionalFeature;

import static domain.Sex.FEMALE;
import static domain.Sex.MALE;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of type interactions with Caches
 *
 * @author Greg Luck
 */
public class TypesTest extends CacheTestSupport<Identifier, String> {

  /**
   * Rule used to exclude tests
   */
  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass()) {

    /**
     * @see org.jsr107.tck.testutil.ExcludeListExcluder#isExcluded(String)
     */
    @Override
    protected boolean isExcluded(String methodName) {
      return "simpleAPINoGenericsAndNoTypeEnforcementStoreByReference".equals(methodName) && !cacheManager.getCachingProvider().isSupported(OptionalFeature.STORE_BY_REFERENCE) || super.isExcluded(methodName);
    }
  };

  private CacheManager cacheManager = getCacheManager();

  private Beagle pistachio = (Beagle) new Beagle().name(new Identifier("Pistachio")).color("tricolor").sex(MALE).weight(7).length(50l).height(30l).neutered(false);
  private RoughCoatedCollie juno = (RoughCoatedCollie) new RoughCoatedCollie().name(new Identifier("Juno")).sex(MALE).weight(7);
  private Dachshund skinny = (Dachshund) new Dachshund().name(new Identifier("Skinny")).sex(MALE).weight(5).neutered(true);
  private Chihuahua tonto = (Chihuahua) new Chihuahua().name(new Identifier("Tonto")).weight(3).sex(MALE).neutered(false);
  private BorderCollie bonzo = (BorderCollie) new BorderCollie().name(new Identifier("Bonzo")).color("tricolor").sex(FEMALE).weight(10);
  private Papillon talker = (Papillon) new Papillon().name(new Identifier("Talker")).color("Black and White").weight(4).sex(MALE);
  private final String cacheName = "sampleCache";

  protected MutableConfiguration<Identifier, String> newMutableConfiguration() {
    return new MutableConfiguration<Identifier, String>().setTypes(Identifier.class, String.class);
  }

  @After
  public void teardown() {
    for (String cacheName : cacheManager.getCacheNames()) {
       cacheManager.destroyCache(cacheName);
    }
    cacheManager.close();
  }

  @Test
  public void sanityCheckTestDomain() {
    Identifier pistachio2Id = new Identifier("Pistachio");
    Beagle pistachio2 = (Beagle) new Beagle().name(pistachio2Id).color("tricolor")
        .sex(MALE).weight(7).length(50l).height(30l).neutered(false);
    Identifier pistachio3Id = new Identifier("Pistachio 2");
    Beagle pistachio3 = (Beagle) new Beagle().name(pistachio3Id).color("tricolor")
        .sex(MALE).weight(7).length(50l).height(30l).neutered(true);

    assertNotEquals(pistachio2Id, pistachio3Id);
    assertNotEquals(pistachio2Id.hashCode(), pistachio3Id.hashCode());

    Identifier2 id2 = new Identifier2("22");
    Identifier2 id3 = new Identifier2("23");
    assertNotEquals(id2, id3);
    assertNotEquals(id2.hashCode(), id3.hashCode());

    assertEquals(pistachio, pistachio2);
    assertEquals(pistachio.hashCode(), pistachio2.hashCode());
    assertNotEquals(pistachio, pistachio3);
    assertNotEquals(pistachio.hashCode(), pistachio3.hashCode());

    pistachio.bay(10, 30);
    skinny.bay(20,32);
    juno.herd();



  }

  /**
   * What happens when you:
   *
   * 1) don't declare using generics and
   * 2) don't specify types during configuration.
   */
  @Test
  public void simpleAPINoGenericsAndNoTypeEnforcementStoreByReference() {

      MutableConfiguration config = new MutableConfiguration().setStoreByValue(false);
      Cache cache = cacheManager.createCache(cacheName, config);
      Identifier2 one = new Identifier2("1");

      //can put different things in
      cache.put(one, "something");
      cache.put(pistachio.getName(), pistachio);
      cache.put(tonto.getName(), tonto);
      cache.put(bonzo.getName(), bonzo);
      cache.put(juno.getName(), juno);
      cache.put(talker.getName(), talker);

      try {
        cache.put(skinny.getName(), skinny);
      } catch(Exception e) {
        //not serializable expected
      }
      //can get them out
      Identifier2 one_ = new Identifier2("1");
      Assert.assertEquals(one, one_);
      Assert.assertEquals(one.hashCode(), one_.hashCode());
      assertNotNull(cache.get(one_));
      assertNotNull(cache.get(one));
      assertNotNull(cache.get(pistachio.getName()));

      //can remove them
      assertTrue(cache.remove(one));
      assertTrue(cache.remove(pistachio.getName()));
  }

  /**
   * What happens when you:
   *
   * 1) don't declare using generics and
   * 2) don't specify types during configuration.
   */
  @Test
  public void simpleAPINoGenericsAndNoTypeEnforcementStoreByValue() {

    MutableConfiguration config = new MutableConfiguration();
    Cache cache = cacheManager.createCache(cacheName, config);
    Identifier2 one = new Identifier2("1");

    //can put different things in
    //But not non-serializable things
    //cache.put(one, "something");
    cache.put(1L, "something");
    cache.put(pistachio.getName(), pistachio);
    cache.put(tonto.getName(), tonto);
    cache.put(bonzo.getName(), bonzo);
    cache.put(juno.getName(), juno);
    cache.put(talker.getName(), talker);

    try {
      cache.put(skinny.getName(), skinny);
    } catch(Exception e) {
      //not serializable expected
    }
    //can get them out
    assertNotNull(cache.get(1L));
    assertNotNull(cache.get(pistachio.getName()));

    //can remove them
    assertTrue(cache.remove(1L));
    assertTrue(cache.remove(pistachio.getName()));
  }

  /**
   * What happens when you:
   *
   * 1) declare using generics and
   * 2) don't specify types during configuration.
   */
  @Test
  public void simpleAPIWithGenericsAndNoTypeEnforcement() {

    MutableConfiguration config = new MutableConfiguration<String, Integer>();
    Cache<Identifier, Dog> cache = cacheManager.createCache(cacheName, config);


    //Types are restricted
    //Cannot put in wrong types
    //cache.put(1, "something");

    //can put in
    cache.put(pistachio.getName(), pistachio);
    cache.put(tonto.getName(), tonto);

    //cannot get out wrong key types
    //assertNotNull(cache.get(1));
    assertNotNull(cache.get(pistachio.getName()));
    assertNotNull(cache.get(tonto.getName()));

    //cannot remove wrong key types
    //assertTrue(cache.remove(1));
    assertTrue(cache.remove(pistachio.getName()));
    assertTrue(cache.remove(tonto.getName()));

  }


  /**
   * What happens when you:
   *
   * 1) declare using generics with a super class
   * 2) declare using configuration with a sub class
   *
   * Set generics to Identifier and Dog
   * Bypass generics with a raw MutableConfiguration but set runtime to Identifier and Hound
   *
   * The configuration checking gets done on put.
   */
  @Test
  public void genericsEnforcementAndStricterTypeEnforcement() {

    //configure the cache
    MutableConfiguration config = new MutableConfiguration<>();
    config.setTypes(Identifier.class, Hound.class);
    Cache<Identifier, Dog> cache = cacheManager.createCache(cacheName, config);

    //Types are restricted and types are enforced
    //Cannot put in wrong types
    //cache.put(1, "something");

    //can put in
    cache.put(pistachio.getName(), pistachio);
    //can put in with generics but possibly not with configuration as not a hound
    try {
      cache.put(tonto.getName(), tonto);
    } catch (ClassCastException e) {
      //expected but not mandatory. The RI throws these.
    }

    //cannot get out wrong key types
    //assertNotNull(cache.get(1));
    assertNotNull(cache.get(pistachio.getName()));
    //not necessarily
    //assertNotNull(cache.get(tonto.getName()));

    //cannot remove wrong key types
    //assertTrue(cache.remove(1));
    assertTrue(cache.remove(pistachio.getName()));
    //not necessarily
    //assertTrue(cache.remove(tonto.getName()));
  }


  /**
   * Same as above but using the shorthand Caching to acquire.
   * Should work the same.
   */
  @Test
  public void genericsEnforcementAndStricterTypeEnforcementFromCaching() {

    //configure the cache
    MutableConfiguration config = new MutableConfiguration<>();
    config.setTypes(Identifier.class, Hound.class);
    Cache<Identifier, Dog> cache = cacheManager.createCache(cacheName, config);

    //Types are restricted and types are enforced
    //Cannot put in wrong types
    //cache.put(1, "something");

    //can put in
    cache.put(pistachio.getName(), pistachio);
    //can put in with generics but possibly not with configuration as not a hound
    try {
      cache.put(tonto.getName(), tonto);
    } catch (ClassCastException e) {
      //expected but not mandatory. The RI throws these.
    }

    //cannot get out wrong key types
    //assertNotNull(cache.get(1));
    assertNotNull(cache.get(pistachio.getName()));
    //not necessarily
    //assertNotNull(cache.get(tonto.getName()));

    //cannot remove wrong key types
    //assertTrue(cache.remove(1));
    assertTrue(cache.remove(pistachio.getName()));
    //not necessarily
    //assertTrue(cache.remove(tonto.getName()));
  }

  /**
   * What happens when you:
   *
   * 1) declare using generics and
   * 2) specify types during configuration but using Object.class, which is permissive
   */
  @Test
  public void simpleAPITypeEnforcementObject() {


    //configure the cache
    MutableConfiguration<Object, Object> config = new MutableConfiguration<>();
    config.setTypes(Object.class, Object.class);

    //create the cache
    Cache<Object, Object> cache = cacheManager.createCache("simpleCache4", config);

    //can put different things in
    cache.put(1, "something");
    cache.put(pistachio.getName(), pistachio);
    cache.put(tonto.getName(), tonto);
    cache.put(bonzo.getName(), bonzo);
    cache.put(juno.getName(), juno);
    cache.put(talker.getName(), talker);
    try {
      cache.put(skinny.getName(), skinny);
    } catch(Exception e) {
      //not serializable expected
    }
    //can get them out
    assertNotNull(cache.get(1));
    assertNotNull(cache.get(pistachio.getName()));

    //can remove them
    assertTrue(cache.remove(1));
    assertTrue(cache.remove(pistachio.getName()));
  }
}
