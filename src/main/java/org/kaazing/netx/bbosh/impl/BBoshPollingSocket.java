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

package org.kaazing.netx.bbosh.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.kaazing.netx.bbosh.BBoshStrategy;

final class BBoshPollingSocket extends BBoshSocket {

    private static final int STATUS_OPEN = 0;
    private static final int STATUS_READ_CLOSED = 1 << 0;
    private static final int STATUS_WRITE_CLOSED = 1 << 1;
    private static final int STATUS_CLOSED = STATUS_READ_CLOSED | STATUS_WRITE_CLOSED;

    private final Object lock;
    private final URL location;
    private final HttpURLConnection[] connections;
    private final InputStream input;
    private final OutputStream output;
    private int sequenceNo;
    private int status;

    BBoshPollingSocket(URL location, int initialSequenceNo, BBoshStrategy strategy) {
        this.location = location;
        this.connections = new HttpURLConnection[strategy.getRequests()];
        this.lock = new Object();
        this.sequenceNo = initialSequenceNo;
        this.status = STATUS_OPEN;
        this.input = new BBoshInputStream();
        this.output = new BBoshOutputStream();
    }

    @Override
    InputStream getInputStream() {
        return input;
    }

    @Override
    OutputStream getOutputStream() {
        return output;
    }

    @Override
    public void close() throws IOException {
        switch (status) {
        case STATUS_CLOSED:
            break;
        default:
            try {
                HttpURLConnection connection = newClosable(acquireSequenceNo());
                switch (connection.getResponseCode()) {
                case 200:
                case 404:
                    break;
                default:
                    throw new IOException("Close failed");
                }
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

    private HttpURLConnection newReadable(int sequenceNo) throws IOException {

        HttpURLConnection newConnection = (HttpURLConnection) location.openConnection();
        newConnection.setRequestMethod("GET");
        newConnection.setRequestProperty("Accept", "application/octet-stream");
        newConnection.setRequestProperty("X-Sequence-No", Integer.toString(sequenceNo));
        newConnection.setDoOutput(true);
        newConnection.setDoInput(true);

        connections[sequenceNo % connections.length] = newConnection;
        return newConnection;
    }

    private HttpURLConnection newWritable(int sequenceNo) throws IOException {

        HttpURLConnection newConnection = (HttpURLConnection) location.openConnection();
        newConnection.setRequestMethod("PUT");
        newConnection.setRequestProperty("Accept", "application/octet-stream");
        newConnection.setRequestProperty("Content-Type", "application/octet-stream");
        newConnection.setRequestProperty("X-Sequence-No", Integer.toString(sequenceNo));
        newConnection.setDoOutput(true);
        newConnection.setDoInput(true);

        connections[sequenceNo % connections.length] = newConnection;
        return newConnection;
    }

    private HttpURLConnection newClosable(int sequenceNo) throws IOException {

        HttpURLConnection newConnection = (HttpURLConnection) location.openConnection();
        newConnection.setRequestMethod("DELETE");
        newConnection.setRequestProperty("Accept", "application/octet-stream");
        newConnection.setRequestProperty("Content-Type", "application/octet-stream");
        newConnection.setRequestProperty("X-Sequence-No", Integer.toString(sequenceNo));
        newConnection.setDoOutput(false);
        newConnection.setDoInput(true);

        connections[sequenceNo % connections.length] = newConnection;
        return newConnection;
    }

    private int acquireSequenceNo() throws IOException {
        while (connections[sequenceNo % connections.length] != null) {
            synchronized (lock) {
                try {
                    lock.wait();
                }
                catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        }
        assert connections[sequenceNo % connections.length] == null;
        return sequenceNo++;
    }


    final class BBoshInputStream extends InputStream {

        private int readerIndex = sequenceNo;

        @Override
        public int read() throws IOException {
            while (true) {
                HttpURLConnection readable = readable();
                try {
                    InputStream stream = readable.getInputStream();
                    int read = stream.read();

                    if (read == -1 || stream.available() == 0) {
                        stream.close();
                        advanceIndex();
                    }

                    if (read != -1) {
                        return read;
                    }
                }
                catch (IOException e) {
                    // status code 404 throws FileNotFoundException (!)
                    advanceIndex();
                    return -1;
                }
            }
        }

        @Override
        public void close() throws IOException {
            if (readerIndex != sequenceNo) {
                HttpURLConnection readable = readable();
                try {
                    InputStream stream = readable.getInputStream();
                    stream.close();
                }
                catch (IOException e) {
                    // ignore (treat as closed)
                }
                finally {
                    advanceIndex();
                }
            }

            switch (status) {
            case STATUS_OPEN:
                status |= STATUS_READ_CLOSED;
                break;
            case STATUS_WRITE_CLOSED:
                BBoshPollingSocket.this.close();
                break;
            default:
                break;
            }
        }

        private void advanceIndex() {
            synchronized (lock) {
                connections[readerIndex % connections.length] = null;
                readerIndex++;
                lock.notify();
            }
        }

        private HttpURLConnection readable() throws IOException {
            HttpURLConnection readable;
            if (readerIndex == sequenceNo) {
                readable = newReadable(acquireSequenceNo());
                assert readable == connections[readerIndex % connections.length];
            }
            else {
                readable = connections[readerIndex % connections.length];
            }
            return readable;
        }
    }

    final class BBoshOutputStream extends OutputStream {

        private int writerIndex = sequenceNo;

        @Override
        public void write(int b) throws IOException {
            while (true) {
                HttpURLConnection writable = writable();
                OutputStream stream = writable.getOutputStream();
                try {
                    // ideally check stream.isClosed()
                    stream.write(b);
                }
                catch (IOException e) {
                    writerIndex++;
                    continue;
                }
                break;
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            while (true) {
                HttpURLConnection writable = writable();
                OutputStream stream = writable.getOutputStream();
                try {
                    // ideally check stream.isClosed()
                    stream.write(b);
                }
                catch (IOException e) {
                    writerIndex++;
                    continue;
                }
                break;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            while (true) {
                HttpURLConnection writable = writable();
                OutputStream stream = writable.getOutputStream();
                try {
                    // ideally check stream.isClosed()
                    stream.write(b, off, len);
                }
                catch (IOException e) {
                    writerIndex++;
                    continue;
                }
                break;
            }
        }

        @Override
        public void flush() throws IOException {
            if (writerIndex != sequenceNo) {
                HttpURLConnection writable = writable();
                OutputStream stream = writable.getOutputStream();
                stream.close();
                writerIndex++;
            }
        }

        @Override
        public void close() throws IOException {
            HttpURLConnection writable = writable();
            OutputStream stream = writable.getOutputStream();
            stream.close();
            writerIndex++;

            switch (status) {
            case STATUS_OPEN:
                status |= STATUS_WRITE_CLOSED;
                break;
            case STATUS_READ_CLOSED:
                BBoshPollingSocket.this.close();
                break;
            default:
                break;
            }
        }

        private HttpURLConnection writable() throws IOException {
            HttpURLConnection writable;
            do {
                if (writerIndex == sequenceNo) {
                    writable = newWritable(acquireSequenceNo());
                }
                else {
                    writable = connections[writerIndex % connections.length];
                }
            } while (!writable.getDoOutput());
            return writable;
        }

    }
}
