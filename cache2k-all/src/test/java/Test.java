/*
 * #%L
 * cache2k "all" transitional artifact
 * %%
 * Copyright (C) 2000 - 2019 headissue GmbH, Munich
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
import org.cache2k.CacheManager;
import org.cache2k.core.util.Log;

import static org.junit.Assert.assertTrue;

/**
 * Quick test to see whether warning message pops up.
 *
 * @author Jens Wilke
 */
public class Test {

  @org.junit.Test
  public void test() {
    Log.SuppressionCounter c = new Log.SuppressionCounter();
    Log.registerSuppression("org.cache2k.extra.allTransition.WarnAboutCache2kAllUsage", c);
    CacheManager.getInstance();
    assertTrue(c.getWarnCount() > 0);
  }

}
