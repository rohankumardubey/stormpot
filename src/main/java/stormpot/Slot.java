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
 * A Slot represents a location in a Pool where objects can be allocated into,
 * so that they can be claimed and released. The Slot is used by the allocated
 * Poolables as a call-back, to tell the pool when an object is released.
 * <p>
 * The individual pool implementations provide their own Slot implementations.
 * 
 * @author Chris Vest &lt;mr.chrisvest@gmail.com&gt;
 * @see Poolable
 */
public interface Slot {
  /**
   * Signal to the pool that the currently claim object at this slot, has been
   * released.
   * <p>
   * It is a user error to release a slot that is not currently claimed. It is
   * likewise a user error to release a Slot while inside the Allocators
   * {@link Allocator#allocate(Slot) allocate} method.
   * <p>
   * Pools are free to throw a PoolException if they detect any of these
   * wrong uses, but it is not guaranteed and the exact behaviour is not
   * specified. "Unspecified behaviour" means that dead-locks and infinite
   * loops are fair responses as well. Therefore, heed the advice and don't
   * misuse release!
   * <p>
   * Pools must, however, guarantee that an object is never
   * {@link Allocator#deallocate(Poolable) deallocated} more than once.
   * This guarantee must hold even if release is misused.
   * @param obj A reference to the Poolable object that is allocated for this
   * slot, and is being released.
   */
  void release(Poolable obj);
}
