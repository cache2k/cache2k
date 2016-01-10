package org.cache2k.test.osgi;

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.junit.Ignore;
import org.junit.Test;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

/**
 * Test the OSGi enabled bundle. Not yet working.
 *
 * @author Jens Wilke
 */
@Ignore
@org.junit.runner.RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class OsgiTest {

  @Configuration
  public Option[] config() {
    return options(
      mavenBundle("org.cache2k", "cache2k-all", System.getProperty("cache2k.version", "0.24-SNAPSHOT")),
      junitBundles()
    );
  }

  @Test
  public void test() {
    Cache<String, String> c = CacheBuilder.newCache(String.class, String.class).build();
    System.out.println("hello world");
  }

}
