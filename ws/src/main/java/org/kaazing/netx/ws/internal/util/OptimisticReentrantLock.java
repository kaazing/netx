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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

// ### TODO: 1. happens-before relationship should be established between the thread that releases the lock and the next thread
//              that acquires the lock.
//           2. Evaluate with Martin's sun.misc.Unsafe based AtomicSequence implementation.
//           3. Implement fairness? It's expensive. We don't want to create garbage for fairness. Also, it's not clear whether
//              it is needed.
public class OptimisticReentrantLock implements Lock {
    private final AtomicReference<Thread> owner;
    private final AtomicInteger stamp;

    public OptimisticReentrantLock() {
        this.owner = new AtomicReference<Thread>(null);
        this.stamp = new AtomicInteger(0);
    }

    @Override
    public void lock() {
        Thread currentThread = Thread.currentThread();

        if (currentThread == owner.get()) {
             stamp.incrementAndGet();
        }
        else {
          while (!owner.compareAndSet(null, currentThread)) {
              // Keep spinning till the lock is acquired.
          }
          stamp.set(1);
        }
    }

    @Override
    public void unlock() {
        Thread currentThread = Thread.currentThread();
        if (currentThread == owner.get() && stamp.decrementAndGet() == 0) {
            // Order in which atomics are updated is important.
            // stamp MUST be zero before nulling the owner.
            // Defeat unlock / lock race by setting owner to null only if not already updated.
            owner.compareAndSet(currentThread, null);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    @Override
    public boolean tryLock() {
        Thread currentThread = Thread.currentThread();

        if (currentThread == owner.get()) {
            stamp.incrementAndGet();
            return true;
        }

        boolean locked = owner.compareAndSet(null, currentThread);
        if (locked) {
            stamp.set(1);
        }

        return locked;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Unsupported operation");
    }

    public Thread getOwner() {
        return owner.get();
    }

    public int getStamp() {
        return stamp.get();
    }
}
