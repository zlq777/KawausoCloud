package cn.kawauso.util;

/**
 * {@link WriteFuture}用于异步通知写入结果和执行回调任务，我们可以通过{@link #addListener(WriteFutureEvent, WriteFutureEventListener)}，
 * 对感兴趣的{@link WriteFutureEvent}注册添加{@link WriteFutureEventListener}，在任务完成时接收通知、执行后续操作
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
public interface WriteFuture<T> {

    /**
     * 针对特定的{@link WriteFutureEvent}，注册一个{@link WriteFutureEventListener}
     *
     * @param event {@link WriteFutureEvent}
     * @param listener {@link WriteFutureEventListener}
     * @return {@link WriteFuture}
     */
    WriteFuture<T> addListener(WriteFutureEvent event, WriteFutureEventListener<T> listener);

    /**
     * 成功执行，并通知执行的结果
     *
     * @param result 结果
     */
    void notify(T result);

    /**
     * 取消执行
     */
    void cancel();

}
