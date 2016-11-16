package org.jsr107.tck.management;

import org.jsr107.tck.processor.GetEntryProcessor;
import org.jsr107.tck.processor.NoOpEntryProcessor;
import org.jsr107.tck.processor.RemoveEntryProcessor;
import org.jsr107.tck.processor.SetEntryProcessor;
import org.jsr107.tck.testutil.CacheTestSupport;
import org.jsr107.tck.testutil.ExcludeListExcluder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.jsr107.tck.testutil.TestSupport.MBeanType.CacheStatistics;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;


/**
 * Tests cache statistics
 *
 * @author Greg Luck
 */
public class CacheMBStatisticsBeanTest extends CacheTestSupport<Long, String> {



  @Before
  public void moreSetUp() {
    cache = getCacheManager().getCache(getTestCacheName(), Long.class, String.class);
    cache.getCacheManager().enableStatistics(cache.getName(), true);
  }

  @Override
  protected MutableConfiguration<Long, String> newMutableConfiguration() {
    return new MutableConfiguration<Long, String>().setTypes(Long.class, String.class);
  }

  @Override
  protected MutableConfiguration<Long, String> extraSetup(MutableConfiguration<Long, String> configuration) {
    return configuration.setStoreByValue(true);
  }


  /**
   * Rule used to exclude tests
   */
  @Rule
  public MethodRule rule = new ExcludeListExcluder(this.getClass()) {

    /* (non-Javadoc)
     * @see javax.cache.util.ExcludeListExcluder#isExcluded(java.lang.String)
     */
    @Override
    protected boolean isExcluded(String methodName) {
      if ("testUnwrap".equals(methodName) && getUnwrapClass(CacheManager.class) == null) {
        return true;
      }

      return super.isExcluded(methodName);
    }
  };

  /**
   * Check that zeroes work
   */
  @Test
  public void testCacheStatisticsAllZero() throws Exception {

    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"));

  }

  @Test
  public void testCacheStatistics() throws Exception {
    final float DELTA=1.0f;

    cache.put(1l, "Sooty");
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    Map<Long, String> entries = new HashMap<Long, String>();
    entries.put(2l, "Lucky");
    entries.put(3l, "Prince");
    cache.putAll(entries);
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    //Update. But we count these simply as puts for stats
    cache.put(1l, "Sooty");
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(4L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    cache.putAll(entries);
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(6L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    cache.getAndPut(4l, "Cody");
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(100.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(7L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    cache.getAndPut(4l, "Cody");
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    String value = cache.get(1l);
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(66.0f, (float)lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"), DELTA);
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(33.0f, (float)lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"), DELTA);
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    //now do a second miss
    value = cache.get(1234324324l);
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    //containsKey() should not affect statistics
    assertTrue(cache.containsKey(1l));
    assertFalse(cache.containsKey(1234324324l));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    assertTrue(cache.remove(1L));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    //no update to cache removals as does not exist
    assertFalse(cache.remove(1L));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(8L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    //should update removals as succeeded
    cache.put(1l, "Sooty");
    assertTrue(cache.remove(1L, "Sooty"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(60.0f, (float)lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"), DELTA);
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(40.0f, (float)lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"), DELTA);
    assertEquals(9L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    //should not update removals as remove failed
    assertFalse(cache.remove(1L, "Sooty"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(9L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    cache.clear();
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(9L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    cache.removeAll();
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(9L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    entries.put(21L, "Trinity");
    cache.putAll(entries);
    cache.removeAll();
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(12L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(5L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


    cache.putAll(entries);
    entries.remove(21L);
    cache.removeAll(entries.keySet());
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(15L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(7L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));

    cache.removeAll(entries.keySet());
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(3L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(50.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(15L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(7L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));
  }


  /**
   * The lookup and locking of the key is enough to invoke the hit or miss. No
   * Cache.Entry or MutableEntry operation is required.
   */
  @Test
  public void testCacheStatisticsInvokeEntryProcessorNoOp() throws Exception {

    cache.put(1l, "Sooty");

    //existent key. cache hit even though this entry processor does not call anything
    cache.invoke(1l, new NoOpEntryProcessor<Long, String>());
    cache.invoke(1l, new NoOpEntryProcessor<Long, String>());

    //non-existent key. cache miss.
    cache.invoke(1000l, new NoOpEntryProcessor<Long, String>());

    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"), greaterThanOrEqualTo(66.65f));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"), lessThanOrEqualTo(33.34f));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));
  }



  @Test
  public void testCacheStatisticsInvokeEntryProcessorGet() throws Exception {

    cache.put(1l, "Sooty");

    //cache hit
    String result = cache.invoke(1l, new GetEntryProcessor<Long, String>());

    //existent key. cache hit even though this entry processor does not call anything
    cache.invoke(1l, new NoOpEntryProcessor<Long, String>());

    //non-existent key. cache miss.
    cache.invoke(1000l, new NoOpEntryProcessor<Long, String>());

    assertEquals(result, "Sooty");
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"), greaterThanOrEqualTo(66.65f));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"), lessThanOrEqualTo(33.34f));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));
  }


  @Test
  public void testCacheStatisticsInvokeEntryProcessorUpdate() throws Exception {

    cache.put(1l, "Sooty");
    String result = cache.invoke(1l, new SetEntryProcessor<Long, String>("Trinity"));
    assertEquals(result, "Trinity");
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(100.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(2L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));
  }

  @Test
  public void testCacheStatisticsInvokeEntryProcessorRemove() throws Exception {

    cache.put(1l, "Sooty");
    String result = cache.invoke(1l, new RemoveEntryProcessor<Long, String, String>(true));
    assertEquals(result, "Sooty");
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(100.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));
  }

  @Test
  public void testIterateAndRemove() throws Exception {


    for (long i = 0; i < 100L; i++) {
      String word = "";
      word = word + " " + "Trinity";
      cache.put(i, word);
    }

    Iterator<Cache.Entry<Long, String>> iterator = cache.iterator();
    while (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }

    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(100.0f, lookupManagementAttribute(cache, CacheStatistics, "CacheHitPercentage"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(0f, lookupManagementAttribute(cache, CacheStatistics, "CacheMissPercentage"));
    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CacheGets"));
    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(100L, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CacheEvictions"));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageGetTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AveragePutTime"), greaterThanOrEqualTo(0f));
    assertThat((Float) lookupManagementAttribute(cache, CacheStatistics, "AverageRemoveTime"), greaterThanOrEqualTo(0f));


  }

  @Test
  public void testGetAndReplace() throws Exception
  {
    long hitCount = 0;
    long missCount = 0;
    long putCount = 0;

    String result = cache.getAndReplace(1L, "MissingNoReplace");
    missCount++;

    assertFalse(cache.containsKey(1L));
    assertEquals(null, result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertFalse(cache.containsKey(1L));

    cache.put(1l, "Sooty");
    putCount++;
    assertTrue(cache.containsKey(1L));
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));

    result = cache.getAndReplace(2L, "InvalidReplace");
    missCount++;
    assertEquals(null, result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertFalse(cache.containsKey(2L));

    result = cache.getAndReplace(1L, "Replaced");
    hitCount++;
    putCount++;
    assertEquals("Sooty", result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
  }



  @Test
  public void testReplace() throws Exception
  {
    long hitCount = 0;
    long missCount = 0;
    long putCount = 0;

    boolean result = cache.replace(1L, "MissingNoReplace");
    missCount++;
    assertFalse(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertFalse(cache.containsKey(1L));

    cache.put(1l, "Sooty");
    putCount++;
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));

    assertTrue(cache.containsKey(1L));
    result = cache.replace(2L, "InvalidReplace");
    missCount++;
    assertFalse(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertFalse(cache.containsKey(2L));

    result = cache.replace(1L, "Replaced");
    hitCount++;
    putCount++;
    assertTrue(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
  }

  @Test
  public void testConditionalReplace() throws Exception
  {
    long hitCount = 0;
    long missCount = 0;
    long putCount = 0;

    boolean result = cache.replace(1L, "MissingNoReplace", "NewValue");
    missCount++;
    assertFalse(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertFalse(cache.containsKey(1L));

    cache.put(1l, "Sooty");
    putCount++;
    assertEquals(1L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));

    assertTrue(cache.containsKey(1L));
    result = cache.replace(1L, "Sooty", "Replaced");
    hitCount++;
    putCount++;
    assertTrue(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));

    result = cache.replace(1L, "Sooty", "InvalidReplace");
    hitCount++;
    assertFalse(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
  }


  @Test
  public void testPutIfAbsent() throws Exception
  {
    long hitCount = 0;
    long missCount = 0;
    long putCount = 0;

    boolean result = cache.putIfAbsent(1L, "succeeded");
    putCount++;
    missCount++;
    assertTrue(result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertTrue(cache.containsKey(1L));

    result = cache.putIfAbsent(1L, "succeeded");
    assertFalse(result);
    hitCount++;
    assertEquals(putCount, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
  }

  @Test
  public void testGetAndRemove() throws Exception
  {
    long hitCount = 0;
    long missCount = 0;
    long removeCount = 0;

    String result = cache.getAndRemove(1L);
    missCount++;
    assertEquals(null, result);
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertEquals(removeCount, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertFalse(cache.containsKey(1L));

    cache.put(1L, "added");
    result = cache.getAndRemove(1L);
    hitCount++;
    removeCount++;
    assertEquals("added", result);
    assertEquals(removeCount, lookupManagementAttribute(cache, CacheStatistics, "CacheRemovals"));
    assertEquals(missCount, lookupManagementAttribute(cache, CacheStatistics, "CacheMisses"));
    assertEquals(hitCount, lookupManagementAttribute(cache, CacheStatistics, "CacheHits"));
    assertFalse(cache.containsKey(1L));
  }


  @Test
  public void testExpiryOnCreation() throws Exception {

      // close cache since need to configure cache with ExpireOnCreationPolicy
      CacheManager mgr = cache.getCacheManager();
      mgr.destroyCache(cache.getName());

      MutableConfiguration<Long, String> config = new MutableConfiguration<Long, String>();
      config.setStatisticsEnabled(true);
      config.setTypes(Long.class, String.class);
      config.setExpiryPolicyFactory(FactoryBuilder.factoryOf(ExpireOnCreationPolicy.class));

      cache = mgr.createCache(getTestCacheName(), config);
      cache.put(1L, "hello");
      assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));

      Map<Long, String> map = new HashMap<Long, String>();
      map.put(2L, "goodbye");
      map.put(3L, "world");
      cache.putAll(map);
      assertEquals(0L, lookupManagementAttribute(cache, CacheStatistics, "CachePuts"));
  }

    /**
     * An {@link javax.cache.expiry.ExpiryPolicy} that will expire {@link Cache} entries
     * before they are created.
     */
    public static class ExpireOnCreationPolicy implements ExpiryPolicy
    {
        @Override
        public Duration getExpiryForCreation() {
            return Duration.ZERO;
        }

        @Override
        public Duration getExpiryForAccess() {
            return Duration.ZERO;
        }

        @Override
        public Duration getExpiryForUpdate() {
            return Duration.ZERO;
        }
    }
}
