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
package stormpot;

/**
 * An Allocator is responsible for the creation and destruction of
 * {@link Poolable} objects.
 * <p>
 * This is where the objects in the Pool comes from. Clients of the Stormpot
 * library needs to provide their own Allocator implementations.
 * <p>
 * Implementations of this interface must be thread-safe, because there is no
 * knowing whether pools will try to access it concurrently or not. Generally
 * they will probably not access it concurrently, but since no guarantee can
 * be provided one has expect that concurrent access might occur. The easiest
 * way to achieve this is to just make the
 * {@link Allocator#allocate(Slot) allocate}
 * and {@link Allocator#deallocate(Poolable) deallocate} methods synchronised.
 * @author Chris Vest &lt;mr.chrisvest@gmail.com&gt;
 *
 * @param <T> any type that implements Poolable.
 */
public interface Allocator<T extends Poolable> {

  /**
   * Create a fresh new instance of T for the given slot.
   * <p>
   * The returned {@link Poolable} must obey the contract that, when
   * {@link Poolable#release() release} is called on it, it must delegate
   * the call onto the {@link Slot#release(Poolable) release} method of the here
   * given slot object.
   * <p>
   * RuntimeExceptions thrown by this method may propagate out through the
   * {@link Pool#claim() claim} method of a pool, in the form of being wrapped
   * inside a {@link PoolException}. Pools must be able to handle these
   * exceptions in a sane manner, and are guaranteed to return to a working
   * state if an Allocator stops throwing exceptions from its allocate method.
   * @param slot The slot the pool wish to allocate an object for.
   * Implementors do not need to concern themselves with the details of a
   * pools slot objects. They just have to call release on them as the
   * protocol demands.
   * @return A newly created instance of T. Never <code>null</code>.
   * @throws Exception if the allocation fails.
   */
  T allocate(Slot slot) throws Exception;

  /**
   * Deallocate, if applicable, the given Poolable and free any resources
   * associated with it.
   * <p>
   * This is an opportunity to close any connections or files, flush buffers,
   * empty caches or what ever might need to be done to completely free any
   * resources represented by this Poolable.
   * <p>
   * Note that a Poolable must never touch its slot object after it has been
   * deallocated.
   * <p>
   * Pools, on the other hand, will guarantee that the same object is never
   * deallocated more than once.
   * <p>
   * Note that pools will always silently swallow exceptions thrown by the
   * deallocate method. They do this because there is no knowing whether the
   * deallocation of an object will be done synchronously by a thread calling
   * {@link Poolable#release() release} on a Poolable, or asynchronously by
   * a clean-up thread inside the pool.
   * <p>
   * Deallocation from the release of an expired object, and deallocation from
   * the shut down procedure of a {@link LifecycledPool} behave the same way
   * in this regard. They will both silently swallow any exception thrown.
   * <p>
   * On the other hand, pools are guaranteed to otherwise correctly deal with
   * any exception that might be thrown. The shut down procedure will still
   * complete, and release will still maintain the internal data structures of
   * the pool to make the slot available for new allocations.
   * @param poolable a non-null Poolable instance to be deallocated.
   * @throws Exception if the deallocation encounters an error.
   */
  void deallocate(T poolable) throws Exception;
}