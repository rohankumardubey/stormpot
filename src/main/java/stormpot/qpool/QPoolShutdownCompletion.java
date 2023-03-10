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
package stormpot.qpool;

import java.util.concurrent.TimeUnit;

import stormpot.Completion;

final class QPoolShutdownCompletion implements Completion {
  private final QAllocThread allocThread;
  
  public QPoolShutdownCompletion(QAllocThread allocThread) {
    this.allocThread = allocThread;
  }
  
  public void await() throws InterruptedException {
    allocThread.await();
  }

  public boolean await(long timeout, TimeUnit unit)
      throws InterruptedException {
    if (unit == null) {
      throw new IllegalArgumentException("timeout TimeUnit cannot be null");
    }
    return allocThread.await(timeout, unit);
  }
}