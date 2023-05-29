package cn.kawauso.util;

/**
 * {@link CancelEventListener}是一个功能呢接口，
 * 允许我们通过实现特定的接口方法{@link #onNotifyCancel()}，在未来某个时间点得到异步写入取消的通知。
 *
 * @author RealDragonking
 */
@FunctionalInterface
public interface CancelEventListener {

    /**
     * 等待实现的接口方法，用于接收写入取消的通知
     */
    void onNotifyCancel();

}
