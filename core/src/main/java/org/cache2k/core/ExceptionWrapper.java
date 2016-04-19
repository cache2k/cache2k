package org.cache2k.core;

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

/**
 * We use instances of the exception wrapper for the value field in the entry.
 * This way we can store exceptions without needing additional memory, if no exceptions
 * happen.
 *
 * @author Jens Wilke; created: 2013-07-12
 */
public class ExceptionWrapper {

  Throwable exception;
  int count;
  long since;
  long lastTry;
  long until;

  /**
   * Store an additional exception message with the expiry time.
   * Gets lazily set as soon as an exception is thrown.
   */
  transient String additionalExceptionMessage = null;

  public ExceptionWrapper(Throwable ex) {
    exception = ex;
  }

  public Throwable getException() {
    return exception;
  }

  public String toString() {
    return "ExceptionWrapper{" + exception.toString() + "}";
  }

}
