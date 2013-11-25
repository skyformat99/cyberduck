package ch.cyberduck.ui;

/*
 * Copyright (c) 2002-2010 David Kocher. All rights reserved.
 *
 * http://cyberduck.ch/
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * dkocher@cyberduck.ch
 */

import ch.cyberduck.core.Factory;
import ch.cyberduck.core.FactoryException;
import ch.cyberduck.core.HostKeyCallback;
import ch.cyberduck.core.Protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * @version $Id$
 */
public abstract class HostKeyControllerFactory extends Factory<HostKeyCallback> {

    public abstract HostKeyCallback create(Controller c, Protocol protocol);

    /**
     * Registered factories
     */
    private static final Map<Platform, HostKeyControllerFactory> factories
            = new HashMap<Platform, HostKeyControllerFactory>();

    /**
     * @param c Window controller
     * @return Login controller instance for the current platform.
     */
    public static HostKeyCallback get(final Controller c, final Protocol protocol) {
        if(!factories.containsKey(NATIVE_PLATFORM)) {
            throw new FactoryException(String.format("No implementation for %s", NATIVE_PLATFORM));
        }
        return factories.get(NATIVE_PLATFORM).create(c, protocol);
    }

    public static void addFactory(Platform p, HostKeyControllerFactory f) {
        factories.put(p, f);
    }
}
