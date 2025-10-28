package tech.insight;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import tech.insight.poll.MyThreadPool;
import tech.insight.policy.impl.CallerRunsPolicy;

/**
 * @author gongxuanzhangmelt@gmail.com
 **/
public class Main {
    public static void main(String[] args) {

        MyThreadPool myThreadPool = new MyThreadPool(
            2, 10, 1, 
            TimeUnit.SECONDS, 
            new ArrayBlockingQueue<Runnable>(2),
            new CallerRunsPolicy()
        );

        for (int i = 0; i < 8; i++) {
            final int fi = i;
            myThreadPool.execute(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(Thread.currentThread().getName() + " " + fi);
            });
        }
        System.out.println("主线程没有被阻塞");
    }
}
