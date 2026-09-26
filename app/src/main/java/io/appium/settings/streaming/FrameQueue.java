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

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Bounded frame queue that drops the oldest buffered frame instead of blocking
 * the producer when full, so a slow consumer never stalls screen capture.
 */
public class FrameQueue {
    private final BlockingQueue<Frame> queue;

    public FrameQueue(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void offer(Frame frame) {
        while (!queue.offer(frame)) {
            queue.poll();
        }
    }

    public Frame take() throws InterruptedException {
        return queue.take();
    }
}
