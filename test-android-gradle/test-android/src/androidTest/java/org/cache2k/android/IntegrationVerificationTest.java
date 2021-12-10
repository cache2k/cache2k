package org.cache2k.android;

/*
 * #%L
 * cache2k Android integration
 * %%
 * Copyright (C) 2000 - 2021 headissue GmbH, Munich
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

import org.junit.jupiter.api.Test;

/**
 * Verify the integration between Cache2k and the Android environment
 * by checking if the classpath is correctly populated with cache2k's codebase.
 * <p>
 * The presence of this test will make the module's test suite non-empty,
 * causing transitive tests from JAR dependencies to be executed as well.
 * If this one didn't exist, no other test would be executed by Gradle either.
 *
 * @author Marcel Schnelle
 */
class IntegrationVerificationTest {

  @Test
  void test() throws Exception {
    Class.forName("org.cache2k.CacheManager");
  }
}
