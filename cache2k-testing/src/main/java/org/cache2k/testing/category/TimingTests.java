package org.cache2k.testing.category;

/*
 * #%L
 * cache2k testing
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

/**
 * Mark test that checks some timing. This test may only run on a unloaded machine and not
 * in parallel with other tests. In general tests like this should be avoided. Timing
 * dependent tests may go into the {@link SlowTests}, too, in case the test copes with the
 * fact that the CPU might get no processing time for an indefinite amount of time.
 */
public interface TimingTests {
}
