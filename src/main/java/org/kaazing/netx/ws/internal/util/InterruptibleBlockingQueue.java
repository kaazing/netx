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

package org.kaazing.netx.ws.internal.util;

import java.util.concurrent.ArrayBlockingQueue;

/**
 * Interruptible blocking queue. Overridden blocking methods {@link #put} and
 * {@link #take} can be interrupted in case of end-of-stream.
 *
 * @param <E>   element type
 */
public class InterruptibleBlockingQueue<E> extends ArrayBlockingQueue<E> {
    private static final long serialVersionUID = 1L;

    // Start pushing back as soon as possible.
    private static final int  _QUEUE_CAPACITY = 1;

    private boolean _done;

    public InterruptibleBlockingQueue() {
        super(_QUEUE_CAPACITY, true);
    }

    public synchronized void done() {
        _done = true;
        notifyAll();
        clear();
    }

    public boolean isDone() {
        return _done;
    }

    public synchronized void reset() {
        // Wake up threads that maybe blocked to retrieve data.
        notifyAll();
        clear();
        _done = false;
    }

    // Override to make peek() a blocking call.
    @Override
    public E peek() {
        E el;

        while (((el = super.peek()) == null) && !isDone()) {
            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    String s = "Reader has been interrupted maybe the connection is closed";
                    throw new RuntimeException(s);
                }
            }
        }

        if ((el == null) && isDone()) {
            String s = "Reader has been interrupted maybe the connection is closed";
            throw new RuntimeException(s);
        }

        return el;
    }

    @Override
    public void put(E el) throws InterruptedException {
        synchronized (this) {
            while ((size() == _QUEUE_CAPACITY) && !isDone()) {
                // Push on the network as the messages are not being retrieved.
                wait();
            }

            if (isDone()) {
                notifyAll();
                return;
            }
        }

        super.put(el);

        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public E take() throws InterruptedException {
        E el = null;

        synchronized (this) {
            while (isEmpty() && !isDone()) {
                wait();
            }

            if (isDone()) {
                notifyAll();

                String s = "Reader has been interrupted maybe the connection is closed";
                throw new InterruptedException(s);
            }
        }

        el = super.take();

        synchronized (this) {
            notifyAll();
        }

        return el;
    }
}

