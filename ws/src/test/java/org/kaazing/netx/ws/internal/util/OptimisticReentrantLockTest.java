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

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Semaphore;

import org.junit.Test;

public class OptimisticReentrantLockTest {
    @Test
    public void testLock() {
        OptimisticReentrantLock sl = new OptimisticReentrantLock();

        sl.lock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        sl.unlock();
        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());
    }

    @Test
    public void testWithThreads() {
        final OptimisticReentrantLock sl = new OptimisticReentrantLock();
        final Semaphore semaphore  = new Semaphore(0);

        Thread t1 = new Thread(new Runnable() {

            @Override
            public void run() {
                int i = 0;
                while (i < 5) {
                    sl.lock();
                    assertEquals(Thread.currentThread(), sl.getOwner());
                    assertEquals(1, sl.getStamp());

                    sl.unlock();
                    i++;
                }
                semaphore.release(1);
            }
        });

        Thread t2 = new Thread(new Runnable() {

            @Override
            public void run() {
                int i = 0;
                while (i < 5) {
                    sl.lock();
                    assertEquals(Thread.currentThread(), sl.getOwner());
                    assertEquals(1, sl.getStamp());

                    sl.unlock();
                    i++;
                }
                semaphore.release(1);
            }
        });

        t1.start();
        t2.start();

        try {
            semaphore.acquire(2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());
    }

    @Test
    public void testRedundantUnlock() {
        final OptimisticReentrantLock sl = new OptimisticReentrantLock();

        sl.lock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        sl.unlock();
        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());

        sl.unlock();
        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());
    }

    @Test
    public void testReentrantLock() throws InterruptedException {
        final OptimisticReentrantLock sl = new OptimisticReentrantLock();
        final Semaphore semaphore  = new Semaphore(0);

        sl.lock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        sl.lock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(2, sl.getStamp());

        sl.lock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(3, sl.getStamp());

        Thread t1 = new Thread(new Runnable() {

            @Override
            public void run() {
                sl.lock();
                assertEquals(Thread.currentThread(), sl.getOwner());
                assertEquals(1, sl.getStamp());

                sl.unlock();
                semaphore.release(1);
            }
        });

        t1.start();

        Thread.sleep(50);

        sl.unlock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(2, sl.getStamp());

        sl.unlock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        // This should unblock the child thread to take ownership of the lock.
        sl.unlock();

        semaphore.acquire(1);

        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());
    }

    @Test
    public void testTryLock() throws Exception {
        final OptimisticReentrantLock sl = new OptimisticReentrantLock();
        final Semaphore semaphore  = new Semaphore(0);

        sl.tryLock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        sl.tryLock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(2, sl.getStamp());

        sl.tryLock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(3, sl.getStamp());

        Thread t1 = new Thread(new Runnable() {

            @Override
            public void run() {
                while (!sl.tryLock()) {
                    // Keep spinning till the lock is acquired.
                }

                assertEquals(Thread.currentThread(), sl.getOwner());
                assertEquals(1, sl.getStamp());

                sl.unlock();
                semaphore.release(1);
            }
        });

        t1.start();

        Thread.sleep(50);

        sl.unlock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(2, sl.getStamp());

        sl.unlock();
        assertEquals(Thread.currentThread(), sl.getOwner());
        assertEquals(1, sl.getStamp());

        // This should unblock the child thread to take ownership of the lock.
        sl.unlock();

        semaphore.acquire(1);

        assertEquals(null, sl.getOwner());
        assertEquals(0, sl.getStamp());

    }
}
