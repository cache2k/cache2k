/**
 *  Copyright 2011-2013 Terracotta, Inc.
 *  Copyright 2011-2013 Oracle, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 This package contains infrastructure so that expiry policy can send
 information back to the JUnit test which initiated them so that asserts can
 happen.

 An instance of {@link org.jsr107.tck.expiry.ExpiryPolicyServer} is created in the JUnit test,
 listening on port 10,005.

 This way no assumption is made about whether a expiry policy is running
 in-process or out of process.

 @author Greg Luck
 @author Brian Oliver
 @author Joe Fialli
 */
package org.jsr107.tck.expiry;
