package cn.kawauso.consensus;

import cn.kawauso.util.WriteFuture;
import io.netty.buffer.ByteBuf;

/**
 * {@link RaftStateMachine}定义了Raft分布式共识算法的抽象细节，为子类提供了一个方便进行具体实现的框架。这里我们设计了一个具备输入接口
 * 的状态机，允许我们从外部输入数据，去影响其时序状态。
 *
 * @author RealDragonking
 */
public interface RaftStateMachine {

    /**
     * 启动此{@link RaftStateMachine}的进程
     *
     * @throws Exception 启动过程中出现的异常
     */
    void start() throws Exception;

    /**
     * 关闭此{@link RaftStateMachine}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    void close() throws Exception;

    /**
     * @return 状态机是否正在运行
     */
    boolean isRunning();

    /**
     * 接收并处理来自于candidate节点的竞选请求
     *
     * @param candidateIndex candidate节点的序列号
     * @param candidateTerm candidate节点的任期
     * @param candidateLastEntryIndex candidate节点的LastEntry序列号
     * @param candidateLastEntryTerm candidate节点的LastEntry任期
     * @param inPrevote 是否位于prevote阶段
     * @param prevoteRound prevote的轮数
     */
    void recvVoteRequest(int candidateIndex, long candidateTerm,
                         long candidateLastEntryIndex, long candidateLastEntryTerm,
                         boolean inPrevote, long prevoteRound);

    /**
     * 接收并处理来自于其它节点的竞选响应
     *
     * @param voterTerm 竞选响应节点的任期
     * @param isSuccess 是否成功获取选票，或是通过prevote
     * @param isLeader 竞选响应节点是否是leader
     * @param inPrevote 是否位于prevote阶段
     * @param prevoteRound prevote的轮数
     */
    void recvVoteResponse(long voterTerm,
                          boolean isSuccess, boolean isLeader,
                          boolean inPrevote, long prevoteRound);

    /**
     * 接收并处理来自于leader节点的消息
     *
     * @param leaderIndex leader节点的序列号
     * @param leaderTerm leader节点的任期
     * @param commitEntryIndex 允许当前节点提交并应用的Entry序列号
     * @param entryIndex 新同步的Entry序列号
     * @param entryData 新同步的Entry数据
     */
    void recvMessageFromLeader(int leaderIndex, long leaderTerm,
                               long commitEntryIndex,
                               long entryIndex, ByteBuf entryData);

    /**
     * 接收并处理来自于其它节点针对leader消息的响应
     *
     * @param nodeIndex 响应节点的序列号
     * @param nodeTerm 响应节点的任期
     * @param syncedEntryIndex 响应节点已经完成同步的Entry序列号
     */
    void recvResponseFromOther(int nodeIndex, long nodeTerm, long syncedEntryIndex);

    /**
     * 将已经写入到集群大多数节点、当前节点可以应用的Entry数据进行应用
     *
     * @param entryIndex 可以应用的Entry序列号
     * @param entryData {@link ByteBuf}，可以应用的Entry数据
     * @param future {@link WriteFuture}，仅在Leader节点状态下不为null
     */
    void applyEntryData(long entryIndex, ByteBuf entryData, WriteFuture<?> future);

    /**
     * 将{@link ByteBuf}字节缓冲区中的数据写入整个集群，写入结果会通过异步方式进行通知
     *
     * @param byteBuf {@link ByteBuf}字节缓冲区，并不会修改读指针的位置和引用计数
     *
     * @return 数据是否能够被写入
     */
    <T> WriteFuture<T> writeToCluster(ByteBuf byteBuf);

}
