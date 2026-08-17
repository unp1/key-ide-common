/* This file is part of the KeY IDE integration - https://key-project.org
 * Licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package org.key_project.ide.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ByteChannel;
import java.nio.channels.Channels;

/**
 * A connection backed by a byte channel, used by both socket transports.
 */
final class ChannelConnection implements Connection {

    private final ByteChannel channel;
    private final InputStream input;
    private final OutputStream output;

    ChannelConnection(ByteChannel channel) {
        this.channel = channel;
        this.input = Channels.newInputStream(channel);
        this.output = Channels.newOutputStream(channel);
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
