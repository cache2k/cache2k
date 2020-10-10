package org.cache2k.core.timing;

/*
 * #%L
 * cache2k core implementation
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

import org.cache2k.core.util.DefaultClock;
import org.cache2k.core.api.InternalClock;
import org.cache2k.testing.category.SlowTests;
import org.junit.experimental.categories.Category;

/**
 * @author Jens Wilke
 */
@Category(SlowTests.class)
public class TimingHandlerTestSlow {

  private static final InternalClock CLOCK = DefaultClock.INSTANCE;


}
