package cn.kawauso.util;

/**
 * {@link WriteFuture}用于异步通知写入结果和执行回调任务，我们可以通过{@link #onSuccess(SuccessEventListener)}和{@link #onCancel(CancelEventListener)}，
 * 注册添加{@link SuccessEventListener}，在任务完成时接收通知、执行后续操作
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
public interface WriteFuture<T> {

    /**
     * 针对成功写入事件，注册添加一个{@link SuccessEventListener}监听回调函数
     *
     * @param listener {@link SuccessEventListener}
     * @return {@link WriteFuture}
     */
    WriteFuture<T> onSuccess(SuccessEventListener<T> listener);

    /**
     * 针对取消写入事件，注册添加一个{@link SuccessEventListener}监听回调函数
     *
     * @param listener {@link SuccessEventListener}
     * @return {@link WriteFuture}
     */
    WriteFuture<T> onCancel(CancelEventListener listener);

    /**
     * 通知异步写入的结果
     *
     * @param result 结果
     */
    void notifySuccess(Object result);

    /**
     * 通知异步写入取消
     */
    void notifyCancel();

}
