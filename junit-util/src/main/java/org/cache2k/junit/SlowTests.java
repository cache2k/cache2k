package org.cache2k.junit;

/*
 * #%L
 * Util classes for all JUnit based tests
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

/**
 * Slow tests like stress tests that run for seconds. Timing dependent tests should go
 * to {@link TimingTests} or may go into the {@link SlowTests}, too, in case the test copes
 * with the fact that the CPU might get no processing time for an indefinite amount of time.
 * If this is the case a test should wait at least 8 seconds for a predicted event to happen.
 * Assertions that fail, because they are timing related should indicate in the text message
 * with "(may flap)".
 *
 * @author Jens Wilke
 */
public interface SlowTests {
}
