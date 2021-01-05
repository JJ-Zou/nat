package com.zjj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadExecutorConfig {

    @Bean()
    public ThreadPoolTaskExecutor threadPoolTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        //核心线程数
        executor.setCorePoolSize(8);
        //最大线程数
        executor.setMaxPoolSize(8);
        //队列大小
        executor.setQueueCapacity(100);
        /*
        rejection-policy：当pool已经达到max size的时候，如何处理新任务
        线程池对拒绝任务的处理策略：此处采用了CallerRunsPolicy策略，
        当线程池没有处理能力的时候，该策略会直接在execute方法的调用线程中运行被拒绝的任务；
        如果执行程序已被关闭，则会丢弃该任务
        */
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //设置线程池关闭的时候等待所有任务都完成再继续销毁其他的Bean
        executor.setWaitForTasksToCompleteOnShutdown(true);
        //设置线程池中任务的等待时间，如果超过这个时候还没有销毁就强制销毁，以确保应用最后能够被关闭，而不是阻塞住
        executor.setAwaitTerminationSeconds(60);
        //执行初始化
        executor.initialize();
        return executor;
    }
}
