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

// ### TODO: happens-before relationship should be established between the thread that releases the lock and the next thread that
//           acquires the lock.
public class OptimisticReentrantLock {
//    private final AtomicStampedReference<Thread> lock;
    private final AtomicReference<Thread> reference;
    private final AtomicInteger stamp;

    public OptimisticReentrantLock() {
//        lock = new AtomicStampedReference<Thread>(null, 0);
        this.reference = new AtomicReference<Thread>(null);
        this.stamp = new AtomicInteger(0);
    }

    public void lock() {
        boolean lockObtained = false;

        while (!lockObtained) {
            if (Thread.currentThread() == getOwner()) {
                stamp.incrementAndGet();
                lockObtained = true;
                break;
            }

            if (reference.compareAndSet(null, Thread.currentThread()) && stamp.compareAndSet(0, 1)) {
                lockObtained = true;
                break;
            }
        }
//      Thread currentOwner = getOwner();
//      int currentStamp = getStamp();
//
//        while (!lock.compareAndSet((currentOwner != Thread.currentThread() ? null : currentOwner),
//                                   Thread.currentThread(),
//                                   (currentOwner != Thread.currentThread() ? 0 : currentStamp),
//                                   (currentOwner != Thread.currentThread() ? 1 : currentStamp + 1)));
    }

    public void unlock() {
        if (Thread.currentThread() == getOwner()) {
            int currentStamp = stamp.get();
            if (currentStamp == 1) {
                stamp.set(0);
                reference.compareAndSet(Thread.currentThread(), null);
            }
            else if (currentStamp > 1) {
                stamp.decrementAndGet();
            }
        }
//        Thread currentOwner = getOwner();
//        int currentStamp = getStamp();
//
//        if (currentOwner != Thread.currentThread()) {
//            return;
//        }
//
//        lock.compareAndSet(currentOwner,
//                           (currentStamp > 1 ? Thread.currentThread() : null),
//                           currentStamp,
//                           (currentStamp > 0 ? currentStamp - 1 : 0));
    }

    public Thread getOwner() {
//        return lock.getReference();
        return reference.get();
    }

    public int getStamp() {
//        return lock.getStamp();
        return stamp.get();
    }
}
