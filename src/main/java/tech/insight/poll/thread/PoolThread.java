package tech.insight.poll.thread;

public abstract class PoolThread extends Thread {
    protected final Runnable initialTask;

    public PoolThread(Runnable initialTask) {
        this.initialTask = initialTask;
    }

    protected void runInitialTask() {
        initialTask.run();
    }
}
