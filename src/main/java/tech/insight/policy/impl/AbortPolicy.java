package tech.insight.policy.impl;

import tech.insight.policy.RejectPolicy;
import tech.insight.poll.MyThreadPool;

public class AbortPolicy implements RejectPolicy {
    @Override
    public void reject(Runnable task, MyThreadPool threadPool) {
        throw new RuntimeException("线程池已满，任务被拒绝: " + task);
    }
}