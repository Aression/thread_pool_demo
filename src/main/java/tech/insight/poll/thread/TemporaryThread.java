package tech.insight.poll.thread;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class TemporaryThread extends PoolThread {
    private final BlockingQueue<Runnable> workQueue;
    private final int keepAliveTime;
    private final TimeUnit timeUnit;
    private final List<Thread> temporaryThreads;

    public TemporaryThread(Runnable initialTask, BlockingQueue<Runnable> workQueue, 
                          int keepAliveTime, TimeUnit timeUnit, List<Thread> temporaryThreads) {
        super(initialTask);
        this.workQueue = workQueue;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.temporaryThreads = temporaryThreads;
    }

    @Override
    public void run() {
        runInitialTask();

        while (true) {
            try {
                Runnable task = workQueue.poll(keepAliveTime, timeUnit);
                if (task == null) {
                    break;
                }
                task.run();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println(Thread.currentThread().getName() + " thread terminated!");
        temporaryThreads.remove(Thread.currentThread());
    }
}

