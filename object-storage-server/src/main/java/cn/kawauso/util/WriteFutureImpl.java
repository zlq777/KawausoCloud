package cn.kawauso.util;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link WriteFutureImpl}作为{@link WriteFuture}的默认实现，采用数组作为{@link SuccessEventListener}的存储结构
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
public final class WriteFutureImpl<T> implements WriteFuture<T>{

    private final ListenerList onSuccessListeners;
    private final ListenerList onCancelListeners;
    private volatile boolean hasCancelled;
    private volatile boolean hasComplete;
    private T result;

    public WriteFutureImpl() {
        this.onSuccessListeners = new ListenerList();
        this.onCancelListeners = new ListenerList();
        this.hasCancelled = false;
        this.hasComplete = false;
    }

    /**
     * 针对成功写入事件，注册添加一个{@link SuccessEventListener}监听回调函数
     *
     * @param listener {@link SuccessEventListener}
     * @return {@link WriteFuture}
     */
    @Override
    public WriteFuture<T> onSuccess(SuccessEventListener<T> listener) {

        checkNotNull(listener, "WriteFutureListener cannot be null !");

        synchronized (this) {
            if (hasComplete) {
                listener.onNotifySuccess(result);
            } else if (! hasCancelled) {
                onSuccessListeners.add(listener);
            }
        }

        return this;
    }

    /**
     * 针对取消写入事件，注册添加一个{@link SuccessEventListener}监听回调函数
     *
     * @param listener {@link SuccessEventListener}
     * @return {@link WriteFuture}
     */
    @Override
    public WriteFuture<T> onCancel(CancelEventListener listener) {

        checkNotNull(listener, "WriteFutureListener cannot be null !");

        synchronized (this) {
            if (hasCancelled) {
                listener.onNotifyCancel();
            } else if (! hasComplete) {
                onCancelListeners.add(listener);
            }
        }

        return this;
    }

    /**
     * 成功执行，并通知执行的结果
     *
     * @param result 结果
     */
    @Override
    @SuppressWarnings("unchecked")
    public void notifySuccess(Object result) {
        synchronized (this) {

            if (hasCancelled || hasComplete) {
                return;
            }

            this.hasComplete = true;
            this.result = (T) result;

            for (int i = 0; i < onSuccessListeners.pos; i++) {
                SuccessEventListener<T> listener = (SuccessEventListener<T>) onSuccessListeners.bucket[i];
                listener.onNotifySuccess(this.result);
            }
        }
    }

    /**
     * 通知异步写入取消
     */
    @Override
    public void notifyCancel() {
        synchronized (this) {

            if (hasCancelled || hasComplete) {
                return;
            }

            this.hasCancelled = true;

            for (int i = 0; i < onCancelListeners.pos; i++) {
                CancelEventListener listener = (CancelEventListener) onCancelListeners.bucket[i];
                listener.onNotifyCancel();
            }
        }
    }

    /**
     * {@link ListenerList}实现了一个简易的动态可扩容数组，作为{@link SuccessEventListener}监听回调函数的容器
     *
     * @author RealDragonking
     */
    private static class ListenerList {

        private volatile int pos;
        private Object[] bucket;

        private ListenerList() {
            this(1);
        }

        private ListenerList(int initialCapacity) {
            this.bucket = new Object[initialCapacity];
            this.pos = 0;
        }

        /**
         * 插入一个新的监听回调函数，并且视情况进行扩容
         *
         * @param newListener 新的监听回调函数
         */
        private void add(Object newListener) {
            int len = bucket.length;

            if (pos == len) {
                Object[] newBucket = new Object[len << 1];
                System.arraycopy(bucket, 0, newBucket, 0, len);

                bucket = newBucket;
            }

            bucket[pos ++] = newListener;
        }

    }

}
