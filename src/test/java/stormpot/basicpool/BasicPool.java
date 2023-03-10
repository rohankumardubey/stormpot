/*
 * Copyright 2011 Chris Vest
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package stormpot.basicpool;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import stormpot.Allocator;
import stormpot.Completion;
import stormpot.Config;
import stormpot.LifecycledPool;
import stormpot.PoolException;
import stormpot.Poolable;
import stormpot.Slot;

/**
 * The BasicPool is a minimal implementation of the Pool interface.
 * It was used to help flesh out the API, and can be considered a
 * reference implementation. It is not in any way optimised. Rather,
 * the implementation has been kept as simple and small as possible.
 * 
 * @author Chris Vest &lt;mr.chrisvest@gmail.com&gt;
 *
 * @param <T>
 */
@SuppressWarnings("unchecked")
public class BasicPool<T extends Poolable> implements LifecycledPool<T> {

  private final Allocator<T> allocator;
  private final Poolable[] pool;
  private final BasicSlot[] slots;
  private final AtomicInteger count;
  private final Lock lock;
  private final Condition released;
  private final long ttlMillis;
  private boolean shutdown;

  public BasicPool(Config<T> config) {
    synchronized (config) {
      config.validate();
      int size = config.getSize();
      this.pool = new Poolable[size];
      this.slots = new BasicSlot[size];
      this.ttlMillis = config.getTTLUnit().toMillis(config.getTTL());
      this.allocator = config.getAllocator();
    }
    this.count = new AtomicInteger();
    this.lock = new ReentrantLock();
    this.released = lock.newCondition();
  }

  public T claim(long timeout, TimeUnit unit) throws InterruptedException {
    if (unit == null) {
      throw new IllegalArgumentException("timeout TimeUnit cannot be null");
    }
    return doClaim(timeout, unit);
  }
  
  private T doClaim(long timeout, TimeUnit unit) throws InterruptedException {
    lock.lock();
    try {
      if (shutdown) {
        throw new IllegalStateException("pool is shut down");
      }
      if (Thread.interrupted()) {
        throw new InterruptedException();
      }
      int index = count.get();
      boolean withTimeout = unit != null;
      long maxWaitNanos = withTimeout? unit.toNanos(timeout) : 0;
      while (index == pool.length) {
        if (withTimeout) {
          if (maxWaitNanos > 0) {
            maxWaitNanos = released.awaitNanos(maxWaitNanos);
          } else {
            return null;
          }
        } else {
          released.await();
        }
        index = count.get();
      }
      if (shutdown) {
        throw new IllegalStateException("pool is shut down");
      }
      Poolable obj = pool[index];
      BasicSlot slot = slots[index];
      if (obj == null || slot.expired()) {
        try {
          slot = slot(index);
          if (withTimeout) {
            Object NULL = new Object();
            AtomicReference ref = new AtomicReference(NULL);
            Thread alloc = alloc(ref, slot);
            alloc.join(maxWaitNanos / 1000000);
            Object value = ref.get();
            if (value == NULL) {
              // timeout
              return null;
            }
            if (value instanceof ExceptionHolder) {
              throw ((ExceptionHolder) value).exception;
            }
            obj = (Poolable) value;
          } else {
            obj = allocator.allocate(slot);
          }
        } catch (Exception e) {
          throw new PoolException("Failed allocation", e);
        }
        if (obj == null) {
          throw new PoolException("Allocator returned null");
        }
        slots[index] = slot;
        pool[index] = obj;
      }
      slot.claim();
      count.incrementAndGet();
      return (T) obj;
    } finally {
      lock.unlock();
    }
  }

  private static class ExceptionHolder {
    Exception exception;
  }

  private Thread alloc(final AtomicReference ref, final Slot slot) {
    Runnable runnable  = new Runnable() {
      public void run() {
        try {
          ref.set(allocator.allocate(slot));
        } catch (Exception e) {
          ExceptionHolder holder = new ExceptionHolder();
          holder.exception = e;
          ref.set(holder);
        }
      }
    };
    Thread thread = new Thread(runnable);
    thread.start();
    return thread;
  }

  public T claim() throws InterruptedException {
    return doClaim(0, null);
  }

  private BasicSlot slot(final int index) {
    return new BasicSlot(index, this);
  }

  public Completion shutdown() {
    lock.lock();
    try {
      shutdown = true;
      ShutdownTask shutdownTask = new ShutdownTask();
      shutdownTask.start();
      return shutdownTask;
    } finally {
      lock.unlock();
    }
  }

  /**
   * This class is static because we need to be able to create arrays of
   * BasicSlots. If the BasicSlot class was non-static, then it would
   * inherit the generic type parameter from the outer class and would
   * become transitively generic. This is no good, because you cannot make
   * generic arrays.
   * So we make the class static, and pass a reference to the outer
   * BasicPool through a constructor parameter.
   * @author Chris Vest &lt;mr.chrisvest@gmail.com&gt;
   *
   */
  private final static class BasicSlot implements Slot {
    private final int index;
    private final long expires;
    private boolean claimed;
    private final BasicPool bpool;

    private BasicSlot(int index, BasicPool bpool) {
      this.index = index;
      this.bpool = bpool;
      this.expires = System.currentTimeMillis() + bpool.ttlMillis;
    }

    public boolean expired() {
      if (System.currentTimeMillis() > expires) {
        try {
          bpool.allocator.deallocate(bpool.pool[index]);
        } catch (Exception _) {
          // exceptions from deallocate are ignored as per specification.
        }
        bpool.pool[index] = null;
        return true;
      }
      return false;
    }

    private void claim() {
      claimed = true;
    }
    
    private boolean isClaimed() {
      return claimed;
    }

    public void release(Poolable obj) {
      bpool.lock.lock();
      if (!claimed) {
        bpool.lock.unlock();
        return;
      }
      claimed = false;
      bpool.count.decrementAndGet();
      bpool.released.signalAll();
      bpool.lock.unlock();
    }
  }

  private final class ShutdownTask extends Thread implements Completion {
    private final CountDownLatch completionLatch;
    
    public ShutdownTask() {
      completionLatch = new CountDownLatch(1);
    }
    
    public void run() {
      lock.lock();
      try {
        for (int index = 0; index < pool.length; index++) {
          if (pool[index] == null) {
            continue;
          }
          while(slots[index].isClaimed()) {
            released.awaitUninterruptibly();
          }
          T poolable = (T) pool[index];
          pool[index] = null;
          try {
            allocator.deallocate(poolable);
          } catch (Exception _) {
            // exceptions from deallocate are ignored as per specification.
          }
        }
      } finally {
        completionLatch.countDown();
        lock.unlock();
      }
    }

    public void await() throws InterruptedException {
      completionLatch.await();
    }

    public boolean await(long timeout, TimeUnit unit)
        throws InterruptedException {
      if (unit == null) {
        throw new IllegalArgumentException("timeout TimeUnit cannot be null");
      }
      return completionLatch.await(timeout, unit);
    }
  }
}
