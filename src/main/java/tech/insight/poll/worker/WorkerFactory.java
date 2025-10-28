package tech.insight.poll.worker;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import tech.insight.poll.thread.CoreThread;
import tech.insight.poll.thread.TemporaryThread;

public class WorkerFactory {

    public Thread newCoreWorker(Runnable firstTask,
                                BlockingQueue<Runnable> workQueue,
                                List<Thread> coreThreads,
                                AtomicInteger coreWorkerCount,
                                Runnable onExit) {
        return new CoreThread(firstTask, workQueue) {
            @Override
            public void run() {
                try {
                    super.run();
                } catch (Throwable t) {
                    // prevent crash bubbling
                } finally {
                    coreWorkerCount.decrementAndGet();
                    coreThreads.remove(Thread.currentThread());
                    if (onExit != null) onExit.run();
                }
            }
        };
    }

    public Thread newTempWorker(Runnable firstTask,
                                BlockingQueue<Runnable> workQueue,
                                int keepAliveTime,
                                TimeUnit timeUnit,
                                List<Thread> temporaryThreads,
                                AtomicInteger tempWorkerCount,
                                Runnable onExit) {
        return new TemporaryThread(firstTask, workQueue, keepAliveTime, timeUnit, temporaryThreads) {
            @Override
            public void run() {
                try {
                    super.run();
                } finally {
                    tempWorkerCount.decrementAndGet();
                    temporaryThreads.remove(Thread.currentThread());
                    if (onExit != null) onExit.run();
                }
            }
        };
    }
}


