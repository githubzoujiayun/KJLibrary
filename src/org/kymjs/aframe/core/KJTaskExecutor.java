/*
 * Copyright (c) 2014, KJFrameForAndroid 张涛 (kymjs123@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kymjs.aframe.core;

import java.util.ArrayDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.kymjs.aframe.utils.LogUtils;

import android.os.Handler;
import android.os.Message;

/**
 * 本类修改自 The Android Open SourceProject<br>
 * 使用高并发的线程池并发执行异步任务，用于替换Android自带的AsyncTask，达到多线程执行的最大效率<br>
 * 使用适配器设计思想如果开发者需要使用串行执行Task任务，可手动调用
 * setDefaultExecutor(KJTaskExecutor.mSerialExecutor)方法<br>
 * 
 * <b>使用建议</b> 由于在高并发操作下，不可避免的会产生处理器调度异常<br>
 * 开发者应该在不需要考虑并发错误的情况下使用本类，例如多线程网络请求数据等<br>
 * <b>创建时间</b> 2014-2-28
 * 
 * @param <Params>
 *            启动参数类型
 * @param <Progress>
 *            进度返回类型
 * @param <Result>
 *            结果返回类型
 * @author zhangtao
 * @author kymjs(kymjs123@gmail.com)
 * @version 1.0
 */
public abstract class KJTaskExecutor<Params, Progress, Result> {
    private static final int CORE_POOL_SIZE = 5;// 线程池核心容量
    private static final int MAXIMUM_POOL_SIZE = 128;// 线程池最大容量
    // 当线程数大于核心时，此为终止前多余的空闲线程等待新任务的最长时间
    private static final int KEEP_ALIVE = 1;

    private static final int MESSAGE_POST_RESULT = 0x1;// 消息类型：发送结果
    private static final int MESSAGE_POST_PROGRESS = 0x2;// 消息类型：更新进度
    // 用来发送结果和进度通知，采用UI线程的Looper来处理消息 这就是为什么Task必须在UI线程调用
    private static final InternalHandler mHandler = new InternalHandler();

    // 工作线程
    private final WorkerRunnable<Params, Result> mWorker;
    // 待执行的runnable
    private final FutureTask<Result> mFuture;
    // 静态阻塞式队列，用来存放待执行的任务，初始容量：8个
    private static final BlockingQueue<Runnable> mPoolWorkQueue = new LinkedBlockingQueue<Runnable>(
            8);

    // 原子布尔型，支持高并发访问，标识任务是否被取消
    private final AtomicBoolean mCancelled = new AtomicBoolean();
    // 原子布尔型，支持高并发访问，标识任务是否被使用过
    private final AtomicBoolean mTaskInvoked = new AtomicBoolean();

    // 任务的状态 默认为挂起，即等待执行，其类型标识为易变的（volatile）
    private volatile Status mStatus = Status.PENDING;

    // 任务的三种状态
    public enum Status {
        /** 任务等待执行 */
        PENDING,
        /** 任务正在执行 */
        RUNNING,
        /** 任务已经执行结束 */
        FINISHED,
    }

    // ThreadFactory，通过工厂方法newThread来获取新线程
    private static final ThreadFactory mThreadFactory = new ThreadFactory() {
        // 原子级整数，可以在超高并发下正常工作
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(Runnable r) {
            return new Thread(r, "KJLibrary->KJTaskExecutor #"
                    + mCount.getAndIncrement());
        }
    };

    /**
     * 并发线程池任务执行器，可以用来并行执行任务<br>
     * 与mSerialExecutor（串行）相对应
     */
    public static final Executor mThreadPoolExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE,
            TimeUnit.SECONDS, mPoolWorkQueue, mThreadFactory);
    // 设置默认任务执行器为并行执行
    private static volatile Executor mDefaultExecutor = mThreadPoolExecutor;

    /**
     * 串行任务执行器，其内部实现了串行控制， 循环的取出一个个任务交给上述的并发线程池去执行<br>
     * 与mThreadPoolExecutor（并行）相对应
     */
    public static final Executor mSerialExecutor = new SerialExecutor();

    /**
     * 串行执行器的实现<br>
     * 如果采用串行执行，asyncTask.execute(Params ...)实际上会调用 SerialExecutor的execute方法。
     * {@link #executeOnExecutor}
     */
    private static class SerialExecutor implements Executor {
        // 线性双向队列，用来存储所有的AsyncTask任务
        final ArrayDeque<Runnable> mTasks = new ArrayDeque<Runnable>();
        /** 当前正在执行的AsyncTask任务 */
        Runnable mActive = null;

        public synchronized void execute(final Runnable r) {
            // 将task任务加入到SerialExecutor的双向队列中，也就是让task排队执行
            mTasks.offer(new Runnable() {
                public void run() {
                    try {
                        r.run();
                    } finally {
                        // 当前task执行完毕后，安排下一个执行
                        scheduleNext();
                    }
                }
            });
            // 如果当前没有任务在执行，直接进入执行逻辑
            if (mActive == null) {
                scheduleNext();
            }
        }

        /**
         * 类似适配器设计模式，如果是并行执行任务就不调用上面的方法而直接使用并发执行者执行任务<br>
         * 如果是串行执行任务， 就配合上面的函数将原本是并发执行的代码转换成串行执行
         */
        protected synchronized void scheduleNext() {
            // 从任务队列中取出队列头部的任务，如果有就交给并发线程池去执行
            if ((mActive = mTasks.poll()) != null) {
                mThreadPoolExecutor.execute(mActive);
            }
        }
    }

    /** 为KJTaskExecutor设置默认执行器 */
    public static void setDefaultExecutor(Executor exec) {
        mDefaultExecutor = exec;
    }

    /**
     * 创建一个asynchronous task，这个构造器必须运行于UI线程
     */
    public KJTaskExecutor() {
        mWorker = new WorkerRunnable<Params, Result>() {
            public Result call() throws Exception {
                mTaskInvoked.set(true);

                // 设置线程优先级
                android.os.Process
                        .setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                return postResult(doInBackground(mParams));
            }
        };

        mFuture = new FutureTask<Result>(mWorker) {
            @Override
            protected void done() {
                try {
                    if (!mTaskInvoked.get()) {
                        postResult(get());
                    }
                } catch (InterruptedException e) {
                    LogUtils.e(e.getMessage());
                } catch (ExecutionException e) {
                    throw new RuntimeException(
                            "An error occured while executing doInBackground()",
                            e.getCause());
                } catch (CancellationException e) {
                    if (!mTaskInvoked.get()) {
                        postResult(null);
                    }
                }
            }
        };
    }

    /**
     * doInBackground执行完毕，发送消息
     * 
     * @param result
     * @return
     */
    private Result postResult(Result result) {
        @SuppressWarnings("unchecked")
        Message message = mHandler.obtainMessage(MESSAGE_POST_RESULT,
                new KJTaskResult<Result>(this, result));
        message.sendToTarget();
        return result;
    }

    /*********************** method ***************************/

    /**
     * 返回任务的状态
     */
    public final Status getStatus() {
        return mStatus;
    }

    /**
     * 返回该线程是否已经被取消
     * 
     * @see #cancel(boolean)
     */
    public final boolean isCancelled() {
        return mCancelled.get();
    }

    /**
     * 如果task已经执行完成，或被某些其他原因取消，再调用本方法将返回false；<br>
     * 当本task还没有启动就调用cancel(boolean),那么这个task将从来没有运行，此时会返回true。<br>
     * 如果任务已经启动，则由参数决定执行此任务是否被中断。<br>
     * 
     * @param mayInterruptIfRunning
     *            <tt>true</tt> 表示取消task的执行
     * @return 如果线程不能被取消返回false, 比如它已经正常完成
     */
    public final boolean cancel(boolean mayInterruptIfRunning) {
        mCancelled.set(true);
        return mFuture.cancel(mayInterruptIfRunning);
    }

    /**
     * Waits if necessary for the computation to complete, and then retrieves
     * its result.
     * 
     * @return The computed result.
     * 
     * @throws CancellationException
     *             If the computation was cancelled.
     * @throws ExecutionException
     *             If the computation threw an exception.
     * @throws InterruptedException
     *             If the current thread was interrupted while waiting.
     */
    public final Result get() throws InterruptedException,
            ExecutionException {
        return mFuture.get();
    }

    /**
     * Waits if necessary for at most the given time for the computation to
     * complete, and then retrieves its result.
     * 
     * @param timeout
     *            Time to wait before cancelling the operation.
     * @param unit
     *            The time unit for the timeout.
     * 
     * @return The computed result.
     * 
     * @throws CancellationException
     *             If the computation was cancelled.
     * @throws ExecutionException
     *             If the computation threw an exception.
     * @throws InterruptedException
     *             If the current thread was interrupted while waiting.
     * @throws TimeoutException
     *             If the wait timed out.
     */
    public final Result get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException,
            TimeoutException {
        return mFuture.get(timeout, unit);
    }

    /*********************** start 一个完整的执行周期 ***************************/

    /**
     * 在doInBackground之前调用，用来做初始化工作 所在线程：UI线程
     */
    protected void onPreExecute() {}

    /**
     * 这个方法是我们必须要重写的，用来做后台计算 所在线程：后台线程
     */
    protected abstract Result doInBackground(Params... params);

    /**
     * 打印后台计算进度，onProgressUpdate会被调用<br>
     * 使用内部handle发送一个进度消息，让onProgressUpdate被调用
     */
    protected final void publishProgress(Progress... values) {
        if (!isCancelled()) {
            mHandler.obtainMessage(MESSAGE_POST_PROGRESS,
                    new KJTaskResult<Progress>(this, values))
                    .sendToTarget();
        }
    }

    /**
     * 在publishProgress之后调用，用来更新计算进度 所在线程：UI线程
     */
    protected void onProgressUpdate(Progress... values) {}

    /**
     * 任务结束的时候会进行判断：如果任务没有被取消，则调用onPostExecute;否则调用onCancelled
     */
    private void finish(Result result) {
        if (isCancelled()) {
            onCancelled(result);
        } else {
            onPostExecute(result);
        }
        mStatus = Status.FINISHED;
    }

    /**
     * 在doInBackground之后调用，用来接受后台计算结果更新UI 所在线程：UI线程
     */
    protected void onPostExecute(Result result) {}

    /**
     * 所在线程：UI线程<br>
     * doInBackground执行结束并且{@link #cancel(boolean)} 被调用。<br>
     * 如果本函数被调用则表示任务已被取消，这个时候onPostExecute不会再被调用。
     */
    protected void onCancelled(Result result) {}

    /*********************** end 一个完整的执行周期 ***************************/
    /*********************** core method ***************************/

    /**
     * 这个方法必须在UI线程中调用<br>
     * Note:这个函数将按照任务队列去串行执行后台线程或并发执行线程，这依赖于platform
     * version,从1.6到3.0是并行，3.0以后为串行（为了避免AsyncTask所带来的并发错误）， 如果你一定要并行执行，你可以调用
     * {@link #executeOnExecutor}替代这个方法，并将默认的执行器改为{@link #mThreadPoolExecutor}
     * 
     * @param params
     *            The parameters of the task.
     * @return This instance of KJTaskExecutor.
     * @throws IllegalStateException
     *             If {@link #getStatus()} returns either
     */
    public final KJTaskExecutor<Params, Progress, Result> execute(
            Params... params) {
        return executeOnExecutor(mDefaultExecutor, params);
    }

    /**
     * 必须在UI线程调用此方法<br>
     * 通过这个方法我们可以自定义KJTaskExecutor的执行方式，串行or并行，甚至可以采用自己的Executor 为了实现并行，
     * asyncTask.executeOnExecutor(KJTaskExecutor.mThreadPoolExecutor, params);
     */
    public final KJTaskExecutor<Params, Progress, Result> executeOnExecutor(
            Executor exec, Params... params) {
        if (mStatus != Status.PENDING) {
            switch (mStatus) {
            case RUNNING:
                throw new IllegalStateException(
                        "Cannot execute task:"
                                + " the task is already running.");
            case FINISHED:
                throw new IllegalStateException(
                        "Cannot execute task:"
                                + " the task has already been executed "
                                + "(a task can be executed only once)");
            default:
                break;
            }
        }
        mStatus = Status.RUNNING;
        onPreExecute();
        mWorker.mParams = params;
        exec.execute(mFuture);// 原理{@link #execute(Runnable runnable)}
        // 接着会有#onProgressUpdate被调用，最后是#onPostExecute
        return this;
    }

    /**
     * 提供一个静态方法，方便在外部直接执行一个runnable
     */
    public static void execute(Runnable runnable) {
        mDefaultExecutor.execute(runnable);
    }

    /**
     * KJTaskExecutor内部Handler，用来发送后台计算进度更新消息和计算完成消息
     */
    private static class InternalHandler extends Handler {
        @Override
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public void handleMessage(Message msg) {
            KJTaskResult result = (KJTaskResult) msg.obj;
            switch (msg.what) {
            case MESSAGE_POST_RESULT:
                result.mTask.finish(result.mData[0]);
                break;
            case MESSAGE_POST_PROGRESS:
                result.mTask.onProgressUpdate(result.mData);
                break;
            }
        }
    }

    private static abstract class WorkerRunnable<Params, Result>
            implements Callable<Result> {
        Params[] mParams;
    }

    private static class KJTaskResult<Data> {
        final Data[] mData;
        final KJTaskExecutor<?, ?, ?> mTask;

        KJTaskResult(KJTaskExecutor<?, ?, ?> task, Data... data) {
            mTask = task;
            mData = data;
        }
    }
}
