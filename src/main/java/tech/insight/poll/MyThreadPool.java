package tech.insight.poll;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import tech.insight.policy.RejectPolicy;
import tech.insight.poll.lifecycle.PoolState;
import tech.insight.poll.worker.WorkerFactory;

public class MyThreadPool {
    // 基本参数
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int keepAliveTime;
    private final TimeUnit timeUnit;
    private final BlockingQueue<Runnable> workQueue;
    private final RejectPolicy rejectHandler;

    
    // 并发控制
    private final ReentrantLock mainLock = new ReentrantLock();
    private final AtomicInteger coreWorkerCount = new AtomicInteger(0);
    private final AtomicInteger tempWorkerCount = new AtomicInteger(0);
    private volatile PoolState state = PoolState.RUNNING;
    private final WorkerFactory workerFactory = new WorkerFactory();

    // 线程列表，用同步包装
    private final List<Thread> coreThreads = Collections.synchronizedList(new ArrayList<>());
    private final List<Thread> temporaryThreads = Collections.synchronizedList(new ArrayList<>());

    // 构造方法设置基本参数
    public MyThreadPool(int corePoolSize, int maxPoolSize, int keepAliveTime, TimeUnit timeUnit,
                        BlockingQueue<Runnable> workQueue, RejectPolicy rejectPolicy) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.workQueue = workQueue;
        this.rejectHandler = rejectPolicy;
    }

    // 执行任务
    public void execute(Runnable task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        if (state != PoolState.RUNNING) {
            rejectHandler.reject(task, this);
            return;
        }
        // 优先尝试创建核心线程
        if (coreWorkerCount.get() < corePoolSize) {
            if (addCoreWorker(task)) {
                return;
            }
        }

        // 入队尝试
        if (workQueue.offer(task)) {
            return;
        }

        // 尝试创建临时线程
        if (coreWorkerCount.get() + tempWorkerCount.get() < maxPoolSize) {
            if (addTempWorker(task)) {
                return;
            }
        }

        // 饱和，执行拒绝策略
        rejectHandler.reject(task, this);
    }

    // 创建核心线程
    private boolean addCoreWorker(Runnable firstTask) {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            if (state != PoolState.RUNNING || coreWorkerCount.get() >= corePoolSize) {
                return false;
            }
            Thread thread = workerFactory.newCoreWorker(firstTask, workQueue, coreThreads, coreWorkerCount, this::tryTerminate);
            coreThreads.add(thread);
            coreWorkerCount.incrementAndGet();
            thread.start();
            return true;
        } finally {
            lock.unlock();
        }
    }

    // 创建临时线程
    private boolean addTempWorker(Runnable firstTask) {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            if (state == PoolState.STOP || coreWorkerCount.get() + tempWorkerCount.get() >= maxPoolSize) {
                return false;
            }
            Thread thread = workerFactory.newTempWorker(firstTask, workQueue, keepAliveTime, timeUnit, temporaryThreads, tempWorkerCount, this::tryTerminate);
            temporaryThreads.add(thread);
            tempWorkerCount.incrementAndGet();
            thread.start();
            return true;
        } finally {
            lock.unlock();
        }
    }

    // 生命周期：有序关闭（不接收新任务，尽量处理完队列）
    public void shutdown() {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            if (state.ordinal() >= PoolState.SHUTDOWN.ordinal()) {
                return;
            }
            state = PoolState.SHUTDOWN;
            tryTerminate();
        } finally {
            lock.unlock();
        }
    }

    // 立即关闭：中断所有工作线程，清空队列
    public List<Runnable> shutdownNow() {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            if (state == PoolState.STOP || state == PoolState.TERMINATED) {
                return new ArrayList<>();
            }
            state = PoolState.STOP;
            interruptWorkers();
            List<Runnable> remaining = new ArrayList<>();
            workQueue.drainTo(remaining);
            tryTerminate();
            return remaining;
        } finally {
            lock.unlock();
        }
    }

    public boolean isShutdown() {
        return state.ordinal() >= PoolState.SHUTDOWN.ordinal();
    }

    public boolean isTerminated() {
        return state == PoolState.TERMINATED;
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (System.nanoTime() < deadline) {
            if (isTerminated()) {
                return true;
            }
            Thread.sleep(10);
        }
        return isTerminated();
    }

    private void interruptWorkers() {
        for (Thread t : coreThreads) {
            t.interrupt();
        }
        for (Thread t : temporaryThreads) {
            t.interrupt();
        }
    }

    private void tryTerminate() {
        final ReentrantLock lock = this.mainLock;
        lock.lock();
        try {
            if (state == PoolState.RUNNING) {
                return;
            }
            if (coreWorkerCount.get() == 0 && tempWorkerCount.get() == 0) {
                state = PoolState.TERMINATED;
            }
        } finally {
            lock.unlock();
        }
    }
}
