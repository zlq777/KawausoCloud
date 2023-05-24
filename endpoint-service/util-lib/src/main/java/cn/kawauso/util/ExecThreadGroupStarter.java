package cn.kawauso.util;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadFactory;

/**
 * {@link ExecThreadGroupStarter}负责初始化全局可用的{@link EventExecutor}任务执行线程池
 *
 * @author RealDragonking
 */
@Service
public class ExecThreadGroupStarter {

    /**
     * 初始化全局可用的{@link EventExecutor}任务执行线程池
     *
     * @param execThreads 任务执行线程数量
     * @return {@link EventExecutor}
     */
    @Bean(destroyMethod = "shutdownGracefully")
    public EventExecutor initExecThreadGroup(@Value("${main.exec-threads}") int execThreads) {
        ThreadFactory execThreadFactory = CommonUtils.getThreadFactory("exec", true);
        return new UnorderedThreadPoolEventExecutor(execThreads, execThreadFactory);
    }

}
