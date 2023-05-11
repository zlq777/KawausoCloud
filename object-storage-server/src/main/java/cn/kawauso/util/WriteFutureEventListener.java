package cn.kawauso.util;

/**
 * {@link WriteFutureEventListener}允许我们通过实现特定的接口方法{@link #onNotify(Object)}，在未来某个时间点得到异步写入结果的通知。
 * 当写入操作被取消时，通常结果会被null代替传入。
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
public interface WriteFutureEventListener<T> {

    /**
     * 等待实现的接口方法，用于接收写入结果的通知
     *
     * @param result 结果
     */
    void onNotify(T result);

}
