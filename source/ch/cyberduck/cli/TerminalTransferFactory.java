package ch.cyberduck.cli;

/*
 * Copyright (c) 2002-2014 David Kocher. All rights reserved.
 * http://cyberduck.io/
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
 * feedback@cyberduck.io
 */

import ch.cyberduck.core.Host;
import ch.cyberduck.core.LocaleFactory;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.transfer.DownloadTransfer;
import ch.cyberduck.core.transfer.SyncTransfer;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferItem;
import ch.cyberduck.core.transfer.UploadTransfer;

import java.util.List;

/**
 * @version $Id$
 */
public class TerminalTransferFactory {

    public static Transfer create(final TerminalAction type, final Host host, final List<TransferItem> items) throws BackgroundException {
        final Transfer transfer;
        switch(type) {
            case download:
                transfer = new DownloadTransfer(host, items);
                break;
            case upload:
                transfer = new UploadTransfer(host, items);
                break;
            case synchronize:
                transfer = new SyncTransfer(host, items.iterator().next());
                break;
            default:
                throw new BackgroundException(LocaleFactory.localizedString("Unknown"),
                        String.format("Unknown transfer type %s", type.name()));
        }
        return transfer;
    }
}
