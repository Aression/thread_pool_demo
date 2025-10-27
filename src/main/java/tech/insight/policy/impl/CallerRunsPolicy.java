package tech.insight.policy.impl;

import tech.insight.policy.RejectPolicy;
import tech.insight.poll.MyThreadPool;

public class CallerRunsPolicy implements RejectPolicy {
    @Override
    public void reject(Runnable task, MyThreadPool threadPool) {
        if (!Thread.currentThread().isInterrupted()) {
            task.run();
        }
    }
}