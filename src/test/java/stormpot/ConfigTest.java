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

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ConfigTest {
  Config config;
  
  @Before public void
  setUp() {
    config = new Config();
  }
  
  @Test public void
  sizeMustBeSettable() {
    config.setSize(123);
    assertTrue(config.getSize() == 123);
  }
  
  @Test public void
  ttlMustBeSettable() {
    long ttl = 123;
    TimeUnit unit = TimeUnit.MICROSECONDS;
    config.setTTL(ttl, unit);
    assertTrue(config.getTTL() == ttl && config.getTTLUnit() == unit);
  }
  
  @Test public void
  allocatorMustBeSettable() {
    CountingAllocator allocator = new CountingAllocator();
    config.setAllocator(allocator);
    assertTrue(config.getAllocator() == allocator);
  }
  
  @Test public void
  mustBeCopyableWithSetAllFields() {
    Allocator allocator = new CountingAllocator();
    int size = 987;
    long ttl = 123;
    TimeUnit unit = TimeUnit.MICROSECONDS;
    config.setAllocator(allocator);
    config.setSize(size);
    config.setTTL(ttl, unit);
    
    Config copy = new Config();
    config.setFieldsOn(copy);
    
    assertTrue(
        copy.getAllocator() == allocator &&
        copy.getSize() == size &&
        copy.getTTL() == ttl &&
        copy.getTTLUnit() == unit);
  }
}
