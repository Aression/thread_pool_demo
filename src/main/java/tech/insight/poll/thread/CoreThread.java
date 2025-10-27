package tech.insight.poll.thread;

import java.util.concurrent.BlockingQueue;

public class CoreThread extends PoolThread {
    private final BlockingQueue<Runnable> workQueue;

    public CoreThread(Runnable initialTask, BlockingQueue<Runnable> workQueue) {
        super(initialTask);
        this.workQueue = workQueue;
    }

    @Override
    public void run() {
        runInitialTask();

        while (true) {
            try {
                Runnable task = workQueue.take();
                task.run();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

