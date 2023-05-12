package cn.kawauso.consensus;

import cn.kawauso.util.WriteFuture;
import cn.kawauso.util.WriteFutureImpl;
import io.netty.buffer.ByteBuf;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import io.netty.util.internal.ThreadLocalRandom;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.kawauso.util.CommonUtils.*;
import static cn.kawauso.consensus.RaftMessageType.*;

/**
 * {@link RaftStateMachineImpl}实现了{@link RaftStateMachine}中的主要逻辑，是整个状态机的核心。
 * 对于数据读写层相关基础设施方法，{@link RaftStateMachineImpl}并不关心它们是怎么实现的。
 *
 * @author RealDragonking
 */
public abstract class RaftStateMachineImpl implements RaftStateMachine {

    private static final Logger log = LogManager.getLogger(RaftStateMachine.class);
    private static final int MAX_ENTRY_SIZE = 32768;

    private final Map<Long, PendingEntry> pendingEntryMap;
    private final EventExecutor applyExecutor;
    private final EventExecutor entryCleaner;
    private final ClusterNode[] otherNodes;
    private final long[] indexTempBucket;
    private final Lock locker;
    private final Timer timer;

    private final int index;
    private final int majority;
    private final int sendWindowSize;
    private final int sendIntervalTicks;
    private final int minElectTimeoutTicks;
    private final int maxElectTimeoutTicks;

    private volatile boolean isRunning;
    private volatile boolean inPrevote;
    private volatile long currentTerm;
    private volatile long prevoteRound;
    private volatile long lastEntryIndex;
    private volatile long lastEntryTerm;
    private volatile long commitEntryIndex;
    private volatile long syncedEntryIndex;
    private volatile int successBallots;
    private volatile int votedForIndex;
    private volatile int resetWaitTicks;
    private volatile int waitTicks;
    private RaftState state;

    public RaftStateMachineImpl(int index,
                                int tickValue,
                                int sendInterval,
                                int minElectTimeout,
                                int maxElectTimeout,
                                int sendWindowSize,
                                String[] allNodeAddresses) {

        this.pendingEntryMap = new TreeMap<>();
        this.locker = new ReentrantLock();

        this.index = index;
        this.otherNodes = resolveAddresses(allNodeAddresses);

        int otherNodeNumber = otherNodes.length;

        this.majority = ((otherNodeNumber + 1) >> 1) + 1;
        this.indexTempBucket = new long[otherNodeNumber];

        this.sendWindowSize = sendWindowSize;
        this.sendIntervalTicks = sendInterval / tickValue;
        this.minElectTimeoutTicks = minElectTimeout / tickValue;
        this.maxElectTimeoutTicks = maxElectTimeout / tickValue;

        ThreadFactory threadFactory;

        threadFactory = getThreadFactory("S.M-Timer", false);
        this.timer = new HashedWheelTimer(threadFactory, tickValue, TimeUnit.MILLISECONDS);

        threadFactory = getThreadFactory("apply-executor", false);
        this.applyExecutor = new UnorderedThreadPoolEventExecutor(1, threadFactory);

        threadFactory = getThreadFactory("entry-cleaner", false);
        this.entryCleaner = new UnorderedThreadPoolEventExecutor(1, threadFactory);

        this.state = RaftState.FOLLOWER;
        this.isRunning = false;
        this.inPrevote = false;
    }

    /**
     * 启动此{@link RaftStateMachine}的进程
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        this.currentTerm = readLong("term");
        this.votedForIndex = readInt("voted-for-index");
        this.lastEntryIndex = readLong("last-entry-index");
        this.lastEntryTerm = readLong("last-entry-term");
        this.commitEntryIndex = readLong("commit-entry-index");

        log.info("term={}", currentTerm);
        log.info("voted-for-index={}", votedForIndex);
        log.info("last-entry-index={} last-entry-term={}", lastEntryIndex, lastEntryTerm);
        log.info("commit-entry-index={}", commitEntryIndex);

        this.waitTicks = resetWaitTicks = randomElectTicks();
        this.syncedEntryIndex = commitEntryIndex;
        this.isRunning = true;

        TimerTask electTimerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) {
                timer.newTimeout(this, 0, TimeUnit.MILLISECONDS);
                locker.lock();

                if (waitTicks == 0) {

                    switch (state) {

                       case FOLLOWER:
                           log.info("Timeout ! State has changed to candidate.");
                           state = RaftState.CANDIDATE;
                           waitTicks = resetWaitTicks = randomElectTicks();
                           startNewVote(true);
                           break;

                       case CANDIDATE:
                           log.info("Timeout ! Candidate will restart a new prevote election!");
                           waitTicks = resetWaitTicks = randomElectTicks();
                           startNewVote(true);
                    }

                } else {
                    waitTicks --;
                }

                locker.unlock();
            }
        };

        electTimerTask.run(null);
    }

    /**
     * 关闭此{@link RaftStateMachine}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    @Override
    public void close() throws Exception {
        isRunning = false;
        timer.stop();
        entryCleaner.shutdownGracefully();
        applyExecutor.shutdownGracefully();
    }

    /**
     * @return 状态机是否已经完成启动
     */
    @Override
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 接收并处理来自于candidate节点的竞选请求
     *
     * @param candidateIndex          candidate节点的序列号
     * @param candidateTerm           candidate节点的任期
     * @param candidateLastEntryIndex candidate节点的LastEntry序列号
     * @param candidateLastEntryTerm  candidate节点的LastEntry任期
     * @param inPrevote               是否位于prevote阶段
     * @param prevoteRound            prevote的轮数
     */
    @Override
    public void recvVoteRequest(int candidateIndex, long candidateTerm,
                                long candidateLastEntryIndex, long candidateLastEntryTerm,
                                boolean inPrevote, long prevoteRound) {

        ClusterNode node = findClusterNode(candidateIndex);

        boolean isSuccess = false;

        locker.lock();

        if (candidateTerm > currentTerm) {
            acceptHigherTerm(candidateTerm);
        }

        if (state == RaftState.FOLLOWER && ! inPrevote) {
            waitTicks = resetWaitTicks;
        }

        if (isLastEntryFreshEnough(candidateLastEntryIndex, candidateLastEntryTerm)) {
            if (inPrevote) {
                isSuccess = true;
            } else {
                if (candidateTerm == currentTerm) {

                    if (votedForIndex == -1) {
                        changeVotedForIndex(candidateIndex);
                    }

                    if (candidateIndex == votedForIndex) {
                        isSuccess = true;
                    }
                }
            }
        }

        node.sendVoteResponse(isSuccess, inPrevote, prevoteRound);

        locker.unlock();
    }

    /**
     * 接收并处理来自于其它节点的竞选响应
     *
     * @param voterTerm    竞选响应节点的任期
     * @param isSuccess    是否成功获取选票，或是通过prevote
     * @param isLeader     竞选响应节点是否是leader
     * @param inPrevote    是否位于prevote阶段
     * @param prevoteRound prevote的轮数
     */
    @Override
    public void recvVoteResponse(long voterTerm,
                                 boolean isSuccess, boolean isLeader,
                                 boolean inPrevote, long prevoteRound) {
        locker.lock();

        if (voterTerm > currentTerm) {
            acceptHigherTerm(voterTerm);
        }

        if (voterTerm == currentTerm && state == RaftState.CANDIDATE) {

            if (isLeader) {

                state = RaftState.FOLLOWER;
                waitTicks = resetWaitTicks = randomElectTicks();

            } else if (inPrevote == this.inPrevote) {

                if (! inPrevote || prevoteRound == this.prevoteRound) {
                    changeBallots(isSuccess);
                }
            }
        }

        locker.unlock();
    }

    /**
     * 接收并处理来自于leader节点的消息
     *
     * @param leaderIndex      leader节点的序列号
     * @param leaderTerm       leader节点的任期
     * @param commitEntryIndex 允许当前节点提交并应用的Entry序列号
     * @param entryIndex       新同步的Entry序列号
     * @param entryData        新同步的Entry数据
     */
    @Override
    public void recvMessageFromLeader(int leaderIndex, long leaderTerm,
                                      long commitEntryIndex,
                                      long entryIndex, ByteBuf entryData) {

        ClusterNode leaderNode = findClusterNode(leaderIndex);

        locker.lock();

        try {

            // 对于leader任期小于当前任期的情况，我们仍需要对其返回响应，指示当前正确的任期
            if (leaderTerm < currentTerm) {
                leaderNode.sendResponse(0L);
                return;
            }

            // 遇到更大的leader任期，我们应该变回follower
            if (leaderTerm > currentTerm) {
                acceptHigherTerm(leaderTerm);
            }

            // 当前节点非follower，并且和leader处于同一个任期，强制转换回follower
            if (state != RaftState.FOLLOWER) {
                state = RaftState.FOLLOWER;
                resetWaitTicks = randomElectTicks();
            }

            waitTicks = resetWaitTicks;

            log.info("Receive Message from leader ! " +
                    "leader: {} " +
                    "{Message: enable-commit-entry-index={} new-sync-entry-index={}} " +
                    "{StateMachine: commit-entry-index={} synced-entry-index={}}",
                    leaderIndex, commitEntryIndex, entryIndex, this.commitEntryIndex, syncedEntryIndex);

            // 对于可提交Entry的情况，进行Entry的批量提交
            if (this.commitEntryIndex < commitEntryIndex) {
                for (long i = this.commitEntryIndex + 1; i <= commitEntryIndex; i++) {
                    PendingEntry entry = pendingEntryMap.remove(i);

                    applyEntryData0(i, entry.data, null);
                    changeCommitEntryIndex(commitEntryIndex);
                }
            }

            // 对于新同步的Entry，index落后于当前已同步Index的情况，只需要简单返回当前已同步的EntryIndex即可
            // 当然这也有可能是心跳消息，index为0
            if (entryIndex <= syncedEntryIndex || pendingEntryMap.get(entryIndex) != null) {
                leaderNode.sendResponse(syncedEntryIndex);
                return;
            }

            PendingEntry newEntry = new PendingEntry(entryData.retain(), null);
            pendingEntryMap.put(entryIndex, newEntry);

            long syncedEntryIndex = this.syncedEntryIndex;

            while (pendingEntryMap.get(syncedEntryIndex + 1) != null) {
                syncedEntryIndex ++;
            }

            // 这里当且仅当，接收到的完整Entry序列发生了增长，才会返回新的已同步index
            if (this.syncedEntryIndex < syncedEntryIndex) {

                for (long i = this.syncedEntryIndex + 1; i <= syncedEntryIndex; i++) {
                    PendingEntry entry = pendingEntryMap.get(i);
                    ByteBuf byteBuf = entry.data;

                    writeEntry(i, byteBuf);
                }

                ensureWriteComplete();

                this.syncedEntryIndex = syncedEntryIndex;

                changeLastEntryIndex(syncedEntryIndex);
                changeLastEntryTerm(currentTerm);

                leaderNode.sendResponse(syncedEntryIndex);
            }

        } finally {
            locker.unlock();
            entryData.release();
        }
    }

    /**
     * 接收并处理来自于其它节点针对leader消息的响应
     *
     * @param nodeIndex        响应节点的序列号
     * @param nodeTerm         响应节点的任期
     * @param syncedEntryIndex 响应节点已经完成同步的Entry序列号
     */
    @Override
    public void recvResponseFromOther(int nodeIndex, long nodeTerm, long syncedEntryIndex) {

        ClusterNode node = findClusterNode(nodeIndex);

        locker.lock();

        if (nodeTerm > currentTerm) {
            acceptHigherTerm(nodeTerm);
        }

        if (state == RaftState.LEADER) {

            // 如果已经完成同步的index不小于对应节点视图的左边界，我们应该动态调整新的发送窗口
            if (syncedEntryIndex >= node.leftIndex) {

                long newLeftIndex = syncedEntryIndex + 1;
                long newRightIndex = Math.min(newLeftIndex + sendWindowSize, lastEntryIndex + 1);

                node.commitIndex = syncedEntryIndex;

                tryCommitNewEntry();

                // 一般来讲，如果是正在同步中，syncedEntryIndex是必然要比node的rightIndex小的
                // 这里大于等于，有且只有一种情况，即刚上任的leader初始化获取节点的已同步index
                if (syncedEntryIndex >= node.rightIndex) {
                    loadPendingEntry(newLeftIndex, newRightIndex);

                    node.sendEntrySyncMessage(newLeftIndex, newRightIndex);
                } else {
                    releasePendingEntry(node.leftIndex, newLeftIndex);
                    loadPendingEntry(node.rightIndex, newRightIndex);

                    node.sendEntrySyncMessage(node.rightIndex, newRightIndex);
                }

                node.leftIndex = newLeftIndex;
                node.rightIndex = newRightIndex;
                node.waitTicks = sendIntervalTicks;
            }
        }

        locker.unlock();
    }

    /**
     * 将{@link ByteBuf}字节缓冲区中的数据写入整个集群，写入结果会通过异步方式进行通知。
     * 这里出于UDP数据包大小限制，我们会对长度超过{@link #MAX_ENTRY_SIZE}的{@link ByteBuf}进行异常抛出。
     *
     * @param byteBuf     {@link ByteBuf}字节缓冲区，并不会修改读指针的位置和引用计数
     *
     * @return 数据是否能够被写入
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> WriteFuture<T> writeToCluster(ByteBuf byteBuf) {

        if (byteBuf.readableBytes() > MAX_ENTRY_SIZE) {
            throw new UnsupportedOperationException("ByteBuf is too big ! Max size is 32768 Bytes");
        }

        WriteFuture<?> future = new WriteFutureImpl<>();

        locker.lock();

        try {

            if (state == RaftState.LEADER) {

                long newEntryIndex = lastEntryIndex + 1;

                writeEntry(newEntryIndex, byteBuf);
                ensureWriteComplete();

                writeLong(newEntryIndex + "term", currentTerm);

                changeLastEntryIndex(newEntryIndex);
                changeLastEntryTerm(currentTerm);

                PendingEntry entry = new PendingEntry(byteBuf, future);
                pendingEntryMap.put(newEntryIndex, entry);

                for (ClusterNode node : otherNodes) {
                    if (node.leftIndex == node.rightIndex && node.rightIndex == newEntryIndex) {
                        node.rightIndex ++;
                        entry.refCnt ++;
                        node.sendEntrySyncMessage(newEntryIndex, node.rightIndex);
                    }
                }

            } else {
                future.notifyCancel();
            }

        } finally {
            locker.unlock();
        }

        return (WriteFuture<T>) future;
    }

    /**
     * 使用UDP协议，向指定的{@link InetSocketAddress}发送{@link ByteBuf}中已经写入的数据
     *
     * @param address {@link InetSocketAddress}
     * @param byteBuf {@link ByteBuf}
     */
    protected abstract void sendDatagramPacket(InetSocketAddress address, ByteBuf byteBuf);

    /**
     * 使用一定的分配策略，分配一个用于读写的{@link ByteBuf}字节缓冲区
     *
     * @return {@link ByteBuf}
     */
    protected abstract ByteBuf allocByteBuf();

    /**
     * 从本地缓存中读取long类型的数据项
     *
     * @param key 数据项的键
     * @return 数据项的值，如果不存在则默认返回 0
     */
    protected abstract long readLong(String key);

    /**
     * 向本地缓存中写入long类型的数据项
     *
     * @param key 数据项的键
     * @param value 数据项的值
     */
    protected abstract void writeLong(String key, long value);

    /**
     * 从本地缓存中读取int类型的数据项
     *
     * @param key 数据项的键
     * @return 数据项的值，如果不存在则默认返回 -1
     */
    protected abstract int readInt(String key);

    /**
     * 向本地缓存中写入int类型的数据项
     *
     * @param key 数据项的键
     * @param value 数据项的值
     */
    protected abstract void writeInt(String key, int value);

    /**
     * 从本地缓存中读取Entry数据，并写入{@link ByteBuf}中
     *
     * @param entryIndex Entry的序列号
     */
    protected abstract ByteBuf readEntry(long entryIndex);

    /**
     * 向本地缓存中写入Entry的数据，这不需要增加{@link ByteBuf}的读指针位置
     *
     * @param entryIndex Entry的序列号
     * @param byteBuf {@link ByteBuf}字节缓冲区
     */
    protected abstract void writeEntry(long entryIndex, ByteBuf byteBuf);

    /**
     * 确保所有数据已经写入硬盘
     */
    protected abstract void ensureWriteComplete();

    /**
     * 在一个额外的执行线程中，调用{@link #applyEntryData(long, ByteBuf, WriteFuture)}。这一设计让我们避免了耗时的数据应用过程
     *
     * @param entryIndex 可以应用的Entry序列号
     * @param entryData {@link ByteBuf}，可以应用的Entry数据
     * @param future {@link WriteFuture}，仅在Leader节点状态下不为null
     */
    private void applyEntryData0(long entryIndex, ByteBuf entryData, WriteFuture<?> future) {
        applyExecutor.execute(() -> applyEntryData(entryIndex, entryData, future));
    }

    /**
     * 修改并持久化{@link #currentTerm}字段
     *
     * @param currentTerm 新值
     */
    private void changeCurrentTerm(long currentTerm) {
        this.currentTerm = currentTerm;
        writeLong("term", currentTerm);
    }

    /**
     * 修改并持久化{@link #votedForIndex}字段
     *
     * @param votedForIndex 新值
     */
    private void changeVotedForIndex(int votedForIndex) {
        if (this.votedForIndex != votedForIndex) {
            this.votedForIndex = votedForIndex;
            writeInt("voted-for-index", votedForIndex);
        }
    }

    /**
     * 修改并持久化{@link #lastEntryIndex}字段
     *
     * @param lastEntryIndex LasEntry的序列号
     *
     */
    private void changeLastEntryIndex(long lastEntryIndex) {
        if (this.lastEntryIndex != lastEntryIndex) {
            this.lastEntryIndex = lastEntryIndex;
            writeLong("last-entry-index", lastEntryIndex);
        }
    }

    /**
     * 修改并持久化{@link #lastEntryTerm}字段
     *
     * @param lastEntryTerm LastEntry的任期
     */
    private void changeLastEntryTerm(long lastEntryTerm) {
        if (this.lastEntryTerm != lastEntryTerm) {
            this.lastEntryTerm = lastEntryTerm;
            writeLong("last-entry-term", lastEntryTerm);
        }
    }

    /**
     * 修改并持久化{@link #commitEntryIndex}字段
     *
     * @param commitEntryIndex 已提交的Entry序列号
     */
    private void changeCommitEntryIndex(long commitEntryIndex) {
        this.commitEntryIndex = commitEntryIndex;
        writeLong("commit-entry-index", commitEntryIndex);
    }

    /**
     * 批量加载{@link PendingEntry}缓存，对于不存在的Entry缓存将尝试从硬盘读取，对于已经存在的Entry缓存则是简单的增加引用计数。这个方法只会由leader进行调用
     *
     * @param startIndex 开始加载的Entry序列号
     * @param endIndex 结束加载的Entry序列号
     */
    private void loadPendingEntry(long startIndex, long endIndex) {
        for (long i = startIndex; i < endIndex; i++) {
            PendingEntry entry = pendingEntryMap.get(i);

            if (entry == null) {
                entry = new PendingEntry(readEntry(i), null);
                pendingEntryMap.put(i, entry);
            }

            entry.refCnt ++;
        }
    }

    /**
     * 批量释放对{@link #pendingEntryMap}中的部分Entry缓存的引用，并通过{@link ByteBuf#release()}释放堆外内存占用。这个方法只会由leader进行调用
     *
     * @param startIndex 开始清空的Entry序列号
     * @param endIndex 结束清空的Entry序列号
     */
    private void releasePendingEntry(long startIndex, long endIndex) {
        for (long i = startIndex; i < endIndex; i++) {
            PendingEntry entry = pendingEntryMap.get(i);

            if (-- entry.refCnt == 0) {
                pendingEntryMap.remove(i);
                entryCleaner.execute(entry.data::release);
            }
        }
    }

    /**
     * 清空{@link #pendingEntryMap}中所有的Entry缓存，并通过{@link ByteBuf#release()}释放堆外内存占用
     */
    private void clearPendingEntry() {
        for (PendingEntry entry : pendingEntryMap.values()) {
            entryCleaner.execute(() -> {
                ByteBuf entryData = entry.data;
                entryData.release();

                WriteFuture<?> future = entry.future;
                future.notifyCancel();
            });
        }

        pendingEntryMap.clear();
    }

    /**
     * 尝试更新Entry的提交进度，这个方法只会由leader进行调用
     */
    private void tryCommitNewEntry() {

        final ClusterNode[] otherNodes = this.otherNodes;
        final long[] indexTempBucket = this.indexTempBucket;

        int len = otherNodes.length;

        for (int i = 0; i < len; i++) {
            indexTempBucket[i] = otherNodes[i].commitIndex;
        }

        Arrays.sort(indexTempBucket);

        long newEnableCommitIndex = indexTempBucket[len - majority + 1];

        if (newEnableCommitIndex > commitEntryIndex) {
            for (long index = commitEntryIndex + 1; index <= newEnableCommitIndex; index++) {

                PendingEntry pendingEntry;

                if ((pendingEntry = pendingEntryMap.get(index)) != null) {
                    applyEntryData0(index, pendingEntry.data.retain(), pendingEntry.future);
                } else {
                    applyEntryData0(index, readEntry(index), null);
                }

                changeCommitEntryIndex(index);
            }
        }
    }

    /**
     * 遇到大于本节点任期的情况，主动转换为follower，并且执行相关操作
     *
     * @param higherTerm 其他节点的更高任期
     */
    private void acceptHigherTerm(long higherTerm) {

        changeCurrentTerm(higherTerm);
        changeVotedForIndex(-1);

        if (state == RaftState.LEADER) {
            clearPendingEntry();
        }

        if (state != RaftState.FOLLOWER) {
            state = RaftState.FOLLOWER;
            waitTicks = resetWaitTicks = randomElectTicks();
        }
    }

    /**
     * 将给定的LastEntry信息和自己的进行比较，判断LastEntry是否足够新
     *
     * @param lastEntryIndex LastEntry序列号
     * @param lastEntryTerm LastEntry任期
     * @return LastEntry是否足够新
     */
    private boolean isLastEntryFreshEnough(long lastEntryIndex, long lastEntryTerm) {
        return lastEntryTerm == this.lastEntryTerm && lastEntryIndex >= this.lastEntryIndex
                || lastEntryTerm > this.lastEntryTerm;
    }

    /**
     * 修改并检查当前的选票状态({@link #successBallots})和竞选阶段({@link #inPrevote})，在
     * 临界条件下执行相应的操作
     *
     * @param isSuccess 针对一张选票，或是一次预投票检验的结果
     */
    private void changeBallots(boolean isSuccess) {
        if (isSuccess) {
            if (++ successBallots == majority) {
                if (inPrevote) {
                    startNewVote(false);
                } else {
                    initForLeader();
                }
            }
        }
    }

    /**
     * 启动一次新的选举流程
     *
     * @param inPrevote 当前进行的是否是预投票
     */
    private void startNewVote(boolean inPrevote) {

        this.inPrevote = inPrevote;
        this.successBallots = 1;

        if (inPrevote) {
            prevoteRound ++;
            log.info("A new prevote election has started ! term={} prevote-round={}", currentTerm, prevoteRound);
        } else {
            changeCurrentTerm(currentTerm + 1);
            changeVotedForIndex(index);

            clearPendingEntry();

            log.info("A new vote election has started ! term={}", currentTerm);
        }

        for (ClusterNode node : otherNodes) {
            node.sendVoteRequest();
        }
    }

    /**
     * 初始化相关设置，以成为新任leader节点
     */
    private void initForLeader() {

        this.state = RaftState.LEADER;

        log.info("Congratulation ! State has changed to leader. term={}", currentTerm);

        for (ClusterNode node : otherNodes) {

            node.resetState();

            TimerTask nodeTask = new TimerTask() {
                @Override
                public void run(Timeout timeout) {
                    timer.newTimeout(this, 0, TimeUnit.MILLISECONDS);
                    locker.lock();
                    if (node.waitTicks == 0) {

                        node.waitTicks = sendIntervalTicks;

                        if (node.leftIndex < node.rightIndex) {
                            node.sendEntrySyncMessage(node.leftIndex, node.rightIndex);
                        } else {
                            node.sendHeartbeatMessage();
                        }

                    } else {
                        node.waitTicks --;
                    }
                    locker.unlock();
                }
            };

            timer.newTimeout(nodeTask, 0, TimeUnit.MILLISECONDS);

            node.sendHeartbeatMessage();
        }
    }

    /**
     * 使用给定的字段范围随机化一个follower参与竞选时间/candidate选举超时时间
     *
     * @return follower参与竞选时间/candidate选举超时时间
     */
    private int randomElectTicks() {
        return ThreadLocalRandom.current().nextInt(minElectTimeoutTicks, maxElectTimeoutTicks);
    }

    /**
     * 根据节点的序列号，获取到{@link #otherNodes}里面对应的{@link ClusterNode}
     *
     * @param index 节点序列号
     * @return {@link ClusterNode}
     */
    private ClusterNode findClusterNode(int index) {
        return index < this.index ? otherNodes[index] : otherNodes[index - 1];
    }

    /**
     * 使用给定的节点连接地址，解析并创建集群节点实例
     *
     * @param addresses {@link InetSocketAddress}数组，代表集群节点的真实地址
     * @return ClusterNode数组
     */
    private ClusterNode[] resolveAddresses(String[] addresses) {
        int nodeSize = addresses.length;
        ClusterNode[] otherNodes = new ClusterNode[nodeSize - 1];

        for (int i = 0; i < nodeSize; i++) {
            if (i != index) {
                String[] addressTuple = addresses[i].split(":");
                String host = addressTuple[0].trim();
                int port = Integer.parseInt(addressTuple[1]);

                InetSocketAddress address = new InetSocketAddress(host, port);
                ClusterNode node = new ClusterNode(address);

                if (i < index) {
                    otherNodes[i] = node;
                } else {
                    otherNodes[i - 1] = node;
                }
            }
        }

        return otherNodes;
    }

    /**
     * {@link ClusterNode}定义了一个集群节点的实例，提供了面向对象的交互api
     *
     * @author RealDragonking
     */
    private class ClusterNode {

        private final InetSocketAddress address;
        private volatile long commitIndex;
        private volatile long leftIndex;
        private volatile long rightIndex;
        private volatile int waitTicks;
        private Timeout taskHandle;

        private ClusterNode(InetSocketAddress address) {
            this.address = address;
            resetState();
        }

        /**
         * 向目标节点发送竞选请求
         */
        private void sendVoteRequest() {
            ByteBuf byteBuf = allocByteBuf()
                    .writeInt(VOTE_REQUEST)
                    .writeInt(index)
                    .writeLong(currentTerm)
                    .writeLong(lastEntryIndex)
                    .writeLong(lastEntryTerm)
                    .writeBoolean(inPrevote)
                    .writeLong(prevoteRound);

            sendDatagramPacket(address, byteBuf);
        }

        /**
         * 向目标节点发送竞选响应
         *
         * @param isSuccess 是否成功获取选票，或是通过prevote
         * @param inPrevote 是否位于prevote阶段
         * @param prevoteRound prevote的轮数
         */
        private void sendVoteResponse(boolean isSuccess, boolean inPrevote, long prevoteRound) {
            ByteBuf byteBuf = allocByteBuf()
                    .writeInt(VOTE_RESPONSE)
                    .writeLong(currentTerm)
                    .writeBoolean(isSuccess)
                    .writeBoolean(state == RaftState.LEADER)
                    .writeBoolean(inPrevote)
                    .writeLong(prevoteRound);

            sendDatagramPacket(address, byteBuf);
        }

        /**
         * 向目标节点发送心跳消息
         */
        private void sendHeartbeatMessage() {
            sendDatagramPacket(address, buildLeaderMessage());
        }

        /**
         * 向目标节点发送Entry数据同步消息
         *
         * @param leftIndex 开始发送的Entry序列号
         */
        private void sendEntrySyncMessage(long leftIndex, long rightIndex) {
            for (long i = leftIndex; i < rightIndex; i++) {
                PendingEntry entry = pendingEntryMap.get(i);

                ByteBuf byteBuf = buildLeaderMessage();
                ByteBuf entryData = entry.data;

                byteBuf.writeLong(i);
                byteBuf.writeBytes(entryData, 0, entryData.readableBytes());

                sendDatagramPacket(address, byteBuf);
            }
        }

        /**
         * 向leader节点发送针对心跳消息，或是数据同步消息的响应
         *
         * @param syncedEntryIndex 已完成同步的Entry序列号，我们可以保证这一序列号之前的Entry已经全部完成同步
         */
        private void sendResponse(long syncedEntryIndex) {
            ByteBuf byteBuf = allocByteBuf()
                    .writeInt(FOLLOWER_RESPONSE)
                    .writeInt(index)
                    .writeLong(currentTerm)
                    .writeLong(syncedEntryIndex);

            sendDatagramPacket(address, byteBuf);
        }

        /**
         * @return 构建leader消息的基本元素
         */
        private ByteBuf buildLeaderMessage() {
            return allocByteBuf()
                    .writeInt(LEADER_MESSAGE)
                    .writeInt(index)
                    .writeLong(currentTerm)
                    .writeLong(commitIndex);
        }

        /**
         * 重置此{@link ClusterNode}的临时性数据字段
         */
        private void resetState() {

            this.commitIndex = 0L;
            this.leftIndex = rightIndex = 0L;

            if (taskHandle != null) {
                taskHandle.cancel();
                taskHandle = null;
            }
        }

    }

    /**
     * {@link PendingEntry}定义了一个等待发送的Entry缓存结构体
     *
     * @author RealDragonking
     */
    private static class PendingEntry {

        private final WriteFuture<?> future;
        private final ByteBuf data;
        private volatile int refCnt;

        private PendingEntry(ByteBuf data, WriteFuture<?> future) {
            this.future = future;
            this.data = data;
            this.refCnt = 0;
        }

    }

}
