package cn.kawauso.util;

import java.util.concurrent.ThreadFactory;

/**
 * {@link CommonUtils}是一个方法类，提供了一些全局通用的方法
 *
 * @author RealDragonking
 */
public final class CommonUtils {

    private CommonUtils() {}

    /**
     * 提供一个自带计数功能的{@link ThreadFactory}
     *
     * @param prefixName 前缀名称
     * @param needCount 是否需要对创建的线程进行计数
     * @return {@link ThreadFactory}
     */
    public static ThreadFactory getThreadFactory(String prefixName, boolean needCount) {
        return new ThreadFactory() {
            private int cnt = 0;
            @Override
            public Thread newThread(Runnable r) {
                return needCount ? new Thread(r, prefixName + "-" + cnt++) : new Thread(r, prefixName);
            }
        };
    }

}
