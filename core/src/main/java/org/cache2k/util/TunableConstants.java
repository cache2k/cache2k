package org.cache2k.util;

/*
 * #%L
 * cache2k core package
 * %%
 * Copyright (C) 2000 - 2014 headissue GmbH, Munich
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
 * Marker interface of all classes that contain code constants. For each
 * implementation class these constants a centralized within a class. The
 * rationale behind this is explained in the following.
 *
 * <p/>Wisely chosen constants are sometimes buried within the code.
 * These are the so called "magic numbers". So lets give them a default
 * place.
 *
 * <p/>There may be a need to change such a "constant". This provides a simple system
 * wide mechanism to change a parameter, aka "tune" it. So, this can be used for
 * performance optimizations. It may be also possible to provide a tuning
 * set that goes tunes towards execution time or towards space efficiency.
 *
 * <p/>Testing: Some code has operations that happen very seldom, e.g. for
 * reorganizing. For testing purposes we can trigger these situations by
 * de-tuning.
 *
 * <p/>If there is a constant need to change a constant, please open a change
 * request. Either it is better to change the tuable constant to a real parameter
 * or a assign it to another value which fits the general purpose better.
 *
 * @author Jens Wilke; created: 2014-04-27
 */
public interface TunableConstants {
}
