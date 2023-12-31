package org.avitech.business;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.avitech.exceptions.AvitechException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SynchronizedBuffer<T> implements Buffer<T> {
  private static final int ARBITRARY_BUFFER_LIMIT = 10;
  private static final Logger LOGGER = LoggerFactory.getLogger(SynchronizedBuffer.class);

  // Lock to control synchronization with this buffer
  private final Lock accessLock = new ReentrantLock();

  // conditions to control reading and writing
  private final Condition canWrite = accessLock.newCondition();
  private final Condition canRead = accessLock.newCondition();

  private final Queue<T> queue = new LinkedList<>(); // shared by producer and consumer threads

  @Override
  public void blockingPut(final T command) {
    accessLock.lock();

    try {
      while (queue.size() >= ARBITRARY_BUFFER_LIMIT) {
        canWrite.await(); // wait until buffer is empty
      }

      queue.add(command);

      // signal any threads waiting to read from buffer
      canRead.signalAll();
    } catch (InterruptedException e) {
      LOGGER.error("An error occurred when trying to put a command to queue");
      throw new AvitechException("An exception thrown on probably the canWrite object", e);
    } finally {
      accessLock.unlock();
    }
  }

  @Override
  public T blockingGet() {
    accessLock.lock();

    try {
      // if there is no data to read, place thread in waiting state
      while (queue.isEmpty()) {
        canRead.await(); // wait until buffer is not empty
      }

      final T command = queue.poll();

      // signal any threads waiting for buffer to be empty
      canWrite.signalAll();

      return command;
    } catch (InterruptedException e) {
      LOGGER.error("An error occurred when trying to get a command from queue");
      throw new AvitechException("An exception thrown on probably the canRead object", e);
    } finally {
      accessLock.unlock();
    }
  }

  public Queue<T> getQueue() {
    return queue;
  }
}
