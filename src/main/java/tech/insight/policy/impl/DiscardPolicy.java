package tech.insight.policy.impl;

import tech.insight.policy.RejectPolicy;
import tech.insight.poll.MyThreadPool;

public class DiscardPolicy implements RejectPolicy {
    @Override
    public void reject(Runnable task, MyThreadPool threadPool) {
        // 静默丢弃
        System.out.println("任务被丢弃: " + task);
    }
}