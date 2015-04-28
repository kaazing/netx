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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

// ### TODO: 1. happens-before relationship should be established between the thread that releases the lock and the next thread
//              that acquires the lock.
//           2. Evaluate using Martin's sun.misc.Unsafe based AtomicSequence implementation.
public class OptimisticReentrantLock {
    private final AtomicReference<Thread> reference;
    private final AtomicInteger stamp;

    public OptimisticReentrantLock() {
        this.reference = new AtomicReference<Thread>(null);
        this.stamp = new AtomicInteger(0);
    }

    public void lock() {
        boolean lockObtained = false;

        if (Thread.currentThread() == getOwner()) {
            stamp.incrementAndGet();
            return;
        }

        while (!lockObtained) {
            if (reference.compareAndSet(null, Thread.currentThread())) {
                stamp.set(1);
                lockObtained = true;
            }
        }
    }

    public void unlock() {
        if (Thread.currentThread() == getOwner()) {
            int currentStamp = stamp.get();
            if (currentStamp == 1) {
                // Order in which atomics are reset is important. First, reset the stamp and then the reference.
                stamp.set(0);
                reference.set(null);
            }
            else if (currentStamp > 1) {
                stamp.decrementAndGet();
            }
        }
    }

    public Thread getOwner() {
        return reference.get();
    }

    public int getStamp() {
        return stamp.get();
    }
}
