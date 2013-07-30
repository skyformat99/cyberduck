package ch.cyberduck.core.transfer.copy;

/*
 * Copyright (c) 2002-2011 David Kocher. All rights reserved.
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

import ch.cyberduck.core.*;
import ch.cyberduck.core.exception.BackgroundException;
import ch.cyberduck.core.features.Copy;
import ch.cyberduck.core.features.Directory;
import ch.cyberduck.core.features.Read;
import ch.cyberduck.core.features.Write;
import ch.cyberduck.core.io.AbstractStreamListener;
import ch.cyberduck.core.io.BandwidthThrottle;
import ch.cyberduck.core.io.StreamCopier;
import ch.cyberduck.core.io.StreamListener;
import ch.cyberduck.core.io.ThrottledInputStream;
import ch.cyberduck.core.io.ThrottledOutputStream;
import ch.cyberduck.core.serializer.Deserializer;
import ch.cyberduck.core.serializer.Serializer;
import ch.cyberduck.core.transfer.Transfer;
import ch.cyberduck.core.transfer.TransferAction;
import ch.cyberduck.core.transfer.TransferOptions;
import ch.cyberduck.core.transfer.TransferPathFilter;
import ch.cyberduck.core.transfer.TransferPrompt;
import ch.cyberduck.core.transfer.TransferStatus;
import ch.cyberduck.core.transfer.normalizer.CopyRootPathsNormalizer;
import ch.cyberduck.core.transfer.symlink.DownloadSymlinkResolver;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @version $Id$
 */
public class CopyTransfer extends Transfer {
    private static final Logger log = Logger.getLogger(CopyTransfer.class);

    /**
     * Mapping source to destination files
     */
    protected Map<Path, Path> files = Collections.emptyMap();

    private Session<?> destination;

    /**
     * @param files Source to destination mapping
     */
    public CopyTransfer(final Session session, final Session destination, final Map<Path, Path> files) {
        this(session, destination, new CopyRootPathsNormalizer().normalize(files),
                new BandwidthThrottle(Preferences.instance().getFloat("queue.download.bandwidth.bytes")));
    }

    private CopyTransfer(final Session session, final Session destination,
                         final Map<Path, Path> selected, final BandwidthThrottle bandwidth) {
        super(session, new ArrayList<Path>(selected.keySet()), bandwidth);
        this.destination = destination;
        this.files = selected;
    }

    public <T> CopyTransfer(T serialized, Session s) {
        super(serialized, s, new BandwidthThrottle(Preferences.instance().getFloat("queue.download.bandwidth.bytes")));
        final Deserializer dict = DeserializerFactory.createDeserializer(serialized);
        Object hostObj = dict.objectForKey("Destination");
        if(hostObj != null) {
            destination = SessionFactory.createSession(new Host(hostObj));
        }
        final List destinationsObj = dict.listForKey("Destinations");
        if(destinationsObj != null) {
            this.files = new HashMap<Path, Path>();
            final List<Path> roots = this.getRoots();
            if(destinationsObj.size() == roots.size()) {
                for(int i = 0; i < roots.size(); i++) {
                    this.files.put(roots.get(i), new Path(destinationsObj.get(i)));
                }
            }
        }
    }

    @Override
    public Type getType() {
        return Type.copy;
    }

    @Override
    public <T> T serialize(final Serializer dict) {
        dict.setStringForKey(String.valueOf(this.getType().ordinal()), "Kind");
        dict.setObjectForKey(session.getHost(), "Host");
        if(destination != null) {
            dict.setObjectForKey(destination.getHost(), "Destination");
        }
        List<Path> targets = new ArrayList<Path>();
        for(Path root : this.getRoots()) {
            if(files.containsKey(root)) {
                targets.add(files.get(root));
            }
        }
        dict.setListForKey(new ArrayList<Serializable>(targets), "Destinations");
        dict.setListForKey(this.getRoots(), "Roots");
        dict.setStringForKey(String.valueOf(this.getSize()), "Size");
        dict.setStringForKey(String.valueOf(this.getTransferred()), "Current");
        if(this.getTimestamp() != null) {
            dict.setStringForKey(String.valueOf(this.getTimestamp().getTime()), "Timestamp");
        }
        if(bandwidth != null) {
            dict.setStringForKey(String.valueOf(bandwidth.getRate()), "Bandwidth");
        }
        return dict.getSerialized();
    }

    @Override
    public List<Session<?>> getSessions() {
        final ArrayList<Session<?>> sessions = new ArrayList<Session<?>>(super.getSessions());
        if(destination != null) {
            sessions.add(destination);
        }
        return sessions;
    }

    @Override
    public TransferAction action(boolean resumeRequested, boolean reloadRequested) {
        return TransferAction.ACTION_OVERWRITE;
    }

    @Override
    public TransferPathFilter filter(TransferPrompt prompt, final TransferAction action) throws BackgroundException {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Filter transfer with action %s", action.toString()));
        }
        if(action.equals(TransferAction.ACTION_OVERWRITE)) {
            return new CopyTransferFilter(destination, files);
        }
        return super.filter(prompt, action);
    }

    @Override
    public AttributedList<Path> children(final Path parent) throws BackgroundException {
        if(log.isDebugEnabled()) {
            log.debug(String.format("List children for %s", parent));
        }
        if(parent.attributes().isSymbolicLink()
                && new DownloadSymlinkResolver(this.getRoots()).resolve(parent)) {
            if(log.isDebugEnabled()) {
                log.debug(String.format("Do not list children for symbolic link %s", parent));
            }
            return AttributedList.emptyList();
        }
        else {
            final AttributedList<Path> list = session.list(parent, new DisabledListProgressListener());
            final Path copy = files.get(parent);
            for(Path p : list) {
                files.put(p, new Path(copy, p.getName(), p.attributes().getType()));
            }
            return list;
        }
    }

    @Override
    public void transfer(final Path source, final TransferOptions options, final TransferStatus status) throws BackgroundException {
        if(log.isDebugEnabled()) {
            log.debug(String.format("Transfer file %s with options %s", source, options));
        }
        final Path copy = files.get(source);
        session.message(MessageFormat.format(LocaleFactory.localizedString("Copying {0} to {1}", "Status"),
                source.getName(), copy.getName()));
        if(source.attributes().isFile()) {
            if(session.getHost().equals(destination.getHost())) {
                final Copy feature = session.getFeature(Copy.class, null);
                if(feature != null) {
                    feature.copy(source, copy);
                    addTransferred(source.attributes().getSize());
                    status.setComplete();
                }
                else {
                    this.copy(source, copy, bandwidth, new AbstractStreamListener() {
                        @Override
                        public void bytesSent(long bytes) {
                            addTransferred(bytes);
                        }
                    }, status);
                }
            }
            else {
                this.copy(source, copy, bandwidth, new AbstractStreamListener() {
                    @Override
                    public void bytesSent(long bytes) {
                        addTransferred(bytes);
                    }
                }, status);
            }
        }
        else {
            if(!status.isExists()) {
                session.message(MessageFormat.format(LocaleFactory.localizedString("Making directory {0}", "Status"),
                        copy.getName()));
                destination.getFeature(Directory.class, new DisabledLoginController()).mkdir(copy, null);
            }
        }
    }

    /**
     * Default implementation using a temporary file on localhost as an intermediary
     * with a download and upload transfer.
     *
     * @param copy     Destination
     * @param throttle The bandwidth limit
     * @param listener Callback
     * @param status   Transfer status
     */
    public void copy(final Path file, final Path copy, final BandwidthThrottle throttle,
                     final StreamListener listener, final TransferStatus status) throws BackgroundException {
        InputStream in = null;
        OutputStream out = null;
        try {
            if(file.attributes().isFile()) {
                new StreamCopier(status).transfer(in = new ThrottledInputStream(session.getFeature(Read.class, new DisabledLoginController()).read(file, status), throttle),
                        0, out = new ThrottledOutputStream(destination.getFeature(Write.class, new DisabledLoginController()).write(copy, status), throttle),
                        listener, -1);
            }
        }
        catch(IOException e) {
            throw new DefaultIOExceptionMappingService().map("Cannot copy {0}", e, file);
        }
        finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public String getName() {
        return MessageFormat.format(LocaleFactory.localizedString("Copying {0} to {1}", "Status"),
                files.keySet().iterator().next().getName(), files.values().iterator().next().getName());
    }

    @Override
    public String getLocal() {
        return null;
    }
}