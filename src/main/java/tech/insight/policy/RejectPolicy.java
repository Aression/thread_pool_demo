package tech.insight.policy;

import tech.insight.poll.MyThreadPool;

public interface RejectPolicy {
    void reject(Runnable task, MyThreadPool threadPool);
}