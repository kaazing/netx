/*
 * Copyright 2014, Kaazing Corporation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kaazing.net.bbosh.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.kaazing.net.bbosh.BBoshStrategy;

final class BBoshStreamingSocket extends BBoshSocket {

    private static final int STATUS_OPEN = 0;
    private static final int STATUS_READ_CLOSED = 1 << 0;
    private static final int STATUS_WRITE_CLOSED = 1 << 1;
    private static final int STATUS_CLOSED = STATUS_READ_CLOSED | STATUS_WRITE_CLOSED;

    private final URL location;
    private final int sequenceNo;
    private final BBoshInputStream input;
    private final BBoshOutputStream output;

    private HttpURLConnection connection;
    private int status;

    BBoshStreamingSocket(URL location, int initialSequenceNo, BBoshStrategy strategy) {
        this.location = location;
        this.sequenceNo = initialSequenceNo;
        this.status = STATUS_OPEN;
        this.input = new BBoshInputStream();
        this.output = new BBoshOutputStream();
    }

    @Override
    InputStream getInputStream() throws IOException {
        return input;
    }

    @Override
    OutputStream getOutputStream() throws IOException {
        return output;
    }

    @Override
    public void close() throws IOException {
        switch (status) {
        case STATUS_CLOSED:
            break;
        default:
            try {
                connection.getOutputStream().close();
                connection.getInputStream().close();
            }
            catch (IOException e) {
                // ignore, treat as closed
            }
            finally {
                status = STATUS_CLOSED;
            }
            break;
        }
    }

    private HttpURLConnection connection() throws IOException {
        if (connection == null) {
            HttpURLConnection connection = (HttpURLConnection) location.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("Transfer-Encoding", "chunked");
            connection.setRequestProperty("X-Sequence-No", Integer.toString(sequenceNo));
            connection.setChunkedStreamingMode(1024);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            this.connection = connection;
        }
        return connection;
    }

    final class BBoshInputStream extends InputStream {

        @Override
        public int read() throws IOException {
            return connection().getInputStream().read();
        }

        @Override
        public void close() throws IOException {
            switch (status) {
            case STATUS_OPEN:
                status |= STATUS_READ_CLOSED;
                break;
            case STATUS_WRITE_CLOSED:
                BBoshStreamingSocket.this.close();
                break;
            default:
                break;
            }
        }

    }

    final class BBoshOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            connection().getOutputStream().write(b);
        }

        @Override
        public void write(byte[] b) throws IOException {
            connection().getOutputStream().write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            connection().getOutputStream().write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            connection().getOutputStream().flush();
        }

        @Override
        public void close() throws IOException {
            connection().getOutputStream().close();

            switch (status) {
            case STATUS_OPEN:
                status |= STATUS_WRITE_CLOSED;
                break;
            case STATUS_READ_CLOSED:
                BBoshStreamingSocket.this.close();
                break;
            default:
                break;
            }
        }

    }

}
