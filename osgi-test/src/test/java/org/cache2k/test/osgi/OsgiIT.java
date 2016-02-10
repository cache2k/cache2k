package org.cache2k.test.osgi;

/*
 * #%L
 * osgi-test
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.cache2k.Cache;
import org.cache2k.CacheBuilder;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerMethod;

import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.bundle;

/**
 * Test the OSGi enabled bundle. Tests are run via the failsafe maven plugin and not with
 * surefire, since these are integration tests. This is critical since we must run
 * after the package phase for the the bundle package to exist.
 *
 * @author Jens Wilke
 */
@org.junit.runner.RunWith(PaxExam.class)
@ExamReactorStrategy(PerMethod.class)
public class OsgiIT {

  @Configuration
  public Option[] config() {
    String _userDir = System.getProperty("user.dir");
    String _ownPath = "/osgi-test";
    String _workspaceDir = _userDir;
    if (_workspaceDir.endsWith(_ownPath)) {
      _workspaceDir = _workspaceDir.substring(0,_workspaceDir.length() -  _ownPath.length());
    }
    return options(
      bundle("file:///" + _workspaceDir + "/variant/all/target/cache2k-all-" + System.getProperty("cache2k.version") + ".jar"),
      junitBundles()
    );
  }

  @Test
  public void testSimple() {
    Cache<String, String> c = CacheBuilder.newCache(String.class, String.class).build();
    c.put("abc", "123");
    c.close();
  }

  @Test @Ignore("SPI for marshallers not working.")
  public void testWithSerialization() {
    Cache<String, String> c =
      CacheBuilder.newCache(String.class, String.class)
        .persistence()
        .build();
    c.put("abc", "123");
    c.close();
  }

}
