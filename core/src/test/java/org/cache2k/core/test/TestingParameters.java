package org.cache2k.core.test;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 */
public interface TestingParameters {

  Log RESULT_LOG = LogFactory.getLog("results");

  /**
   * Maximum time in millis we wait for an event to finish. Might need to be increased in loaded environments.
   */
  int MAX_FINISH_WAIT = 60000;

  /**
   * Minimum amount of time that we expect to pass in waiting for an event.
   */
  int MINIMAL_TICK_MILLIS = 3;

}
