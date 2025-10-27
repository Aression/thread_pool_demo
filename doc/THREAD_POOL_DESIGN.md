# 自定义线程池设计与实现原理

## 📋 项目概述

这是一个仿照 `ThreadPoolExecutor` 设计的自定义线程池实现，采用两层线程设计：
- **核心线程（CoreThread）**：长期存活，负责处理常规任务
- **临时线程（TemporaryThread）**：按需创建，超时自动回收

---

## 🔄 运行原理

### 1. 任务提交流程（`execute` 方法）

```
任务提交 → 判断核心线程数 → 提交到队列 → 创建临时线程 → 拒绝策略
   ↓           ↓                 ↓              ↓             ↓
   ↓    [核心线程未满]      [队列未满]      [总数未达最大]  [触发拒绝]
   ↓           ↓                 ↓              ↓
   ↓    创建核心线程        直接返回        创建临时线程
   ↓           ↓                               ↓
   ↓    立即执行任务 +                     立即执行任务 +
   ↓    从队列取任务                        等待超时后终止
```

**详细步骤：**

```42:59:src/main/java/tech/insight/poll/MyThreadPool.java
public void execute(Runnable task) {
    if (coreThreads.size() < corePoolSize) {
        // 步骤1: 核心线程未满，创建核心线程
        Thread thread = new CoreThread(task, workQueue);
        coreThreads.add(thread);
        thread.start();
        return;
    }
    if (workQueue.offer(task)) {
        // 步骤2: 提交到工作队列
        return;
    }
    if (coreThreads.size() + temporaryThreads.size() < maxPoolSize) {
        // 步骤3: 队列满但未达最大线程数，创建临时线程
        Thread thread = new TemporaryThread(task, workQueue, keepAliveTime, timeUnit, temporaryThreads);
        temporaryThreads.add(thread);
        thread.start();
        return;
    }
    // 步骤4: 触发拒绝策略
    rejectHandler.reject(task, this);
}
```

---

## 🧵 线程类型设计

### 2. 核心线程（CoreThread）

**特性：**
- 生命周期：永不终止
- 工作方式：执行初始任务后，从队列无限阻塞获取任务
- 使用场景：维持线程池的基本处理能力

```13:25:src/main/java/tech/insight/poll/thread/CoreThread.java
@Override
public void run() {
    runInitialTask();

    while (true) {
        try {
            Runnable task = workQueue.take();  // 阻塞等待
            task.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
```

### 3. 临时线程（TemporaryThread）

**特性：**
- 生命周期：有超时限制
- 工作方式：执行初始任务后，使用超时 `poll` 获取任务
- 自动回收：超时后自动退出并清理自己

```23:39:src/main/java/tech/insight/poll/thread/TemporaryThread.java
@Override
public void run() {
    runInitialTask();

    while (true) {
        try {
            Runnable task = workQueue.poll(keepAliveTime, timeUnit);
            if (task == null) {  // 超时返回 null
                break;
            }
            task.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    System.out.println(Thread.currentThread().getName() + " thread terminated!");
    temporaryThreads.remove(Thread.currentThread());  // 自动清理
}
```

---

## 🎨 设计改进点

### 改进1: 包结构调整

**优化前：**
```
tech.insight.poll/
  ├── MyThreadPool.java
  ├── PoolThread.java
  ├── CoreThread.java
  └── TemporaryThread.java
```

**优化后：**
```
tech.insight.poll/
  ├── MyThreadPool.java
  └── thread/
      ├── PoolThread.java
      ├── CoreThread.java
      └── TemporaryThread.java
```

**优势：** 更清晰的组织结构，线程相关类独立成包

---

### 改进2: 解决参数传递问题

**问题根源：** `workQueue`、`keepAliveTime` 等是 `MyThreadPool` 的成员变量，线程内部无法访问

**解决方案：** 通过构造函数显式传递依赖

```java
// 核心线程
public CoreThread(Runnable initialTask, BlockingQueue<Runnable> workQueue) {
    super(initialTask);
    this.workQueue = workQueue;
}

// 临时线程
public TemporaryThread(Runnable initialTask, BlockingQueue<Runnable> workQueue, 
                      int keepAliveTime, TimeUnit timeUnit, List<Thread> temporaryThreads) {
    super(initialTask);
    this.workQueue = workQueue;
    this.keepAliveTime = keepAliveTime;
    this.timeUnit = timeUnit;
    this.temporaryThreads = temporaryThreads;
}
```

**优势：**
- ✅ 线程类实现自包含，不依赖外部状态
- ✅ 依赖注入清晰，易于测试
- ✅ 符合面向对象设计原则

---

### 改进3: 分离抽象层

**设计模式：模板方法模式（Template Method Pattern）**

```3:13:src/main/java/tech/insight/poll/thread/PoolThread.java
public abstract class PoolThread extends Thread {
    protected final Runnable initialTask;

    public PoolThread(Runnable initialTask) {
        this.initialTask = initialTask;
    }

    protected void runInitialTask() {
        initialTask.run();
    }
}
```

**优势：**
- 统一的任务启动逻辑
- 子类只需实现 `run()` 方法中的循环逻辑
- 减少重复代码

---

### 改进4: 策略模式实现拒绝策略

```5:7:src/main/java/tech/insight/policy/RejectPolicy.java
public interface RejectPolicy {
    void reject(Runnable task, MyThreadPool threadPool);
}
```

**实现策略：**
- `AbortPolicy`：抛出异常
- `CallerRunsPolicy`：调用者执行
- `DiscardPolicy`：静默丢弃

**优势：**
- 开放封闭原则：易于扩展新的拒绝策略
- 解耦：策略独立于线程池核心逻辑

---

## 📊 执行示例分析

以 `Main.java` 为例：

```java
MyThreadPool myThreadPool = new MyThreadPool(
    2,      // 核心线程数
    4,      // 最大线程数  
    1,      // 临时线程超时时间
    TimeUnit.SECONDS, 
    new ArrayBlockingQueue<Runnable>(2),  // 队列容量为2
    new DiscardPolicy()
);
```

**提交 8 个任务时的处理：**

| 任务 | 处理方式 |
|------|---------|
| 任务0 | 创建 CoreThread-0 |
| 任务1 | 创建 CoreThread-1 |
| 任务2 | 进入队列 |
| 任务3 | 进入队列 |
| 任务4 | 创建 TemporaryThread-2 |
| 任务5 | 创建 TemporaryThread-3 |
| 任务6 | 触发 DiscardPolicy |
| 任务7 | 触发 DiscardPolicy |

---

## 🎯 关键设计亮点

### 1. **两层线程机制**
- 核心线程保证基础吞吐量
- 临时线程处理峰值负载并自动回收

### 2. **优雅的线程回收**
- 临时线程通过超时 `poll` 实现自动回收
- 无需额外的监控线程或定时器

### 3. **阻塞队列缓冲**
- 任务优先进入队列而非立即创建线程
- 减少资源浪费

### 4. **灵活的参数传递**
- 线程类通过构造函数接收所需参数
- 符合依赖注入原则

---

## 🚀 性能特点

| 特性 | 说明 |
|------|------|
| **响应性** | 初始任务立即执行 |
| **吞吐量** | 核心线程持续处理 |
| **资源管理** | 临时线程自动回收 |
| **负载均衡** | 队列缓冲任务分配 |

