package cn.kawauso.util;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * {@link WriteFutureImpl}作为{@link WriteFuture}的默认实现，采用数组作为{@link WriteFutureEventListener}的存储结构
 *
 * @author RealDragonking
 * @param <T> 通知结果的类型
 */
public final class WriteFutureImpl<T> implements WriteFuture<T>{

    private final Array onSuccessListeners;
    private final Array onCancelListeners;
    private volatile boolean hasCancelled;
    private volatile boolean hasComplete;
    private T result;

    public WriteFutureImpl() {
        this.onSuccessListeners = new Array();
        this.onCancelListeners = new Array();
        this.hasCancelled = false;
        this.hasComplete = false;
    }

    /**
     * 针对特定的{@link WriteFutureEvent}，注册一个{@link WriteFutureEventListener}
     *
     * @param event    {@link WriteFutureEvent}
     * @param listener {@link WriteFutureEventListener}
     * @return {@link WriteFuture}
     */
    @Override
    public WriteFuture<T> addListener(WriteFutureEvent event, WriteFutureEventListener<T> listener) {

        checkNotNull(event, "WriteFutureEvent cannot be null !");
        checkNotNull(listener, "WriteFutureListener cannot be null !");

        synchronized (this) {
            switch (event) {

                case ON_SUCCESS:
                    if (hasComplete) {
                        listener.onNotify(result);
                    } else if (! hasCancelled) {
                        onSuccessListeners.add(listener);
                    }
                    break;

                case ON_CANCEL:
                    if (hasCancelled) {
                        listener.onNotify(null);
                    } else if (! hasComplete) {
                        onCancelListeners.add(listener);
                    }
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
    public void notify(T result) {
        synchronized (this) {

            if (hasCancelled || hasComplete) {
                return;
            }

            this.hasComplete = true;
            this.result = result;

            for (Object element : onSuccessListeners.elementData) {
                WriteFutureEventListener<T> listener = (WriteFutureEventListener<T>) element;
                listener.onNotify(result);
            }
        }
    }

    /**
     * 取消执行
     */
    @Override
    @SuppressWarnings("unchecked")
    public void cancel() {
        synchronized (this) {

            if (hasComplete || hasCancelled) {
                return;
            }

            this.hasCancelled = true;

            for (Object element : onSuccessListeners.elementData) {
                WriteFutureEventListener<T> listener = (WriteFutureEventListener<T>) element;
                listener.onNotify(null);
            }
        }
    }

    /**
     * {@link Array}实现了一个简易的动态可扩容数组
     *
     * @author RealDragonking
     */
    private static class Array {

        private volatile int pos;
        private Object[] elementData;

        private Array() {
            this(1);
        }

        private Array(int initialCapacity) {
            this.elementData = new Object[initialCapacity];
            this.pos = 0;
        }

        /**
         * 插入一个元素
         *
         * @param element 元素
         */
        private void add(Object element) {
            int len = elementData.length;

            if (len == pos) {
                Object[] newElementData = new Object[len << 1];
                System.arraycopy(elementData, 0, newElementData, 0, len);

                elementData = newElementData;
            }

            elementData[pos ++] = element;
        }

    }

}
