package org.cache2k.spi;

/*
 * #%L
 * cache2k API
 * %%
 * Copyright (C) 2000 - 2020 headissue GmbH, Munich
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

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.ServiceConfigurationError;

/**
 * @author Jens Wilke
 */
public class SingleProviderResolverTest {

  @Test
  public void testUnknown() {
    assertNull(SingleProviderResolver.resolve(Unknown.class));
  }

  @Test(expected = LinkageError.class)
  public void testUnknownButMandatory() {
    SingleProviderResolver.resolveMandatory(Unknown.class);
  }

  @Test(expected = LinkageError.class)
  public void testNonsense() {
    SingleProviderResolver.resolveMandatory(Integer.class);
  }

  @Test(expected = ServiceConfigurationError.class)
  public void testServiceLoaderConfigurationError() {
    SingleProviderResolver.resolveMandatory(ServiceLoaderConfigurationError.class);
  }

  @Test
  public void testFallbackToServiceLoader() {
    assertNotNull(SingleProviderResolver.resolveMandatory(KnownServiceLoader.class));
  }

  public interface Unknown { }

  public interface KnownSingle { }

  public interface ServiceLoaderConfigurationError { }

  public interface KnownServiceLoader { }

  public static class KnownServiceLoaderImpl implements KnownServiceLoader { }

}
