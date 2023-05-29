package cn.kawauso.util;

/**
 * {@link SuccessEventListener}是一个功能接口，
 * 允许我们通过实现特定的接口方法{@link #onNotifySuccess(Object)}，在未来某个时间点得到异步写入成功的通知
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
@FunctionalInterface
public interface SuccessEventListener<T> {

    /**
     * 等待实现的接口方法，用于接收成功写入的通知
     *
     * @param result 结果
     */
    void onNotifySuccess(T result);

}
