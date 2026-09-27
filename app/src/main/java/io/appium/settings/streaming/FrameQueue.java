/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings.streaming;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

/**
 * Bounded frame queue that drops the oldest buffered frame instead of blocking
 * the producer when full, so a slow consumer never stalls screen capture.
 * FLAG_CONFIG frames (e.g. H.264/HEVC SPS/PPS) are protected from eviction where
 * possible, since losing one can make the rest of the session undecodable.
 */
public class FrameQueue {
    private final int capacity;
    private final Deque<Frame> queue = new ArrayDeque<>();

    public FrameQueue(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void offer(Frame frame) {
        queue.addLast(frame);
        while (queue.size() > capacity && !evictOldestDroppable()) {
            // Every buffered frame is a CONFIG frame (should not happen in practice) -
            // drop the true oldest one anyway rather than growing unbounded.
            queue.pollFirst();
        }
        notify();
    }

    private boolean evictOldestDroppable() {
        Iterator<Frame> it = queue.iterator();
        while (it.hasNext()) {
            if ((it.next().flags & Frame.FLAG_CONFIG) == 0) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    public synchronized Frame take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait();
        }
        return queue.pollFirst();
    }
}
