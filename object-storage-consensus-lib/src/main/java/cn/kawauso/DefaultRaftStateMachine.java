package cn.kawauso;

import cn.kawauso.util.WriteFuture;
import cn.kawauso.consensus.RaftStateMachineBaseImpl;
import cn.kawauso.network.UDPService;
import io.netty.buffer.ByteBuf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * {@link DefaultRaftStateMachine}是一个完整的状态机，最终实现了{@link #applyEntryData(long, ByteBuf, WriteFuture)}方法
 * 和数据应用层的逻辑。基于raft分布式共识算法的保证，我们可以确信每一笔应用的数据都已经写入了集群的大多数节点，具备高可用性和安全性。
 *
 * @author RealDragonking
 */
public final class DefaultRaftStateMachine extends RaftStateMachineBaseImpl {

    private static final Logger log = LogManager.getLogger(DefaultRaftStateMachine.class);

    public DefaultRaftStateMachine(UDPService udpService,
                                   int index,
                                   int tickValue,
                                   int sendInterval,
                                   int minElectTimeout,
                                   int maxElectTimeout,
                                   int sendWindowSize,
                                   String[] allNodeAddresses) throws Exception {

        super(udpService, index, tickValue, sendInterval, minElectTimeout, maxElectTimeout, sendWindowSize, allNodeAddresses);
    }

    /**
     * 将已经写入到集群大多数节点、当前节点可以应用的Entry数据进行应用
     *
     * @param entryIndex 可以应用的Entry序列号
     * @param entryData  {@link ByteBuf}，可以应用的Entry数据
     * @param future  {@link WriteFuture}，仅在Leader节点状态下不为null
     */
    @Override
    public void applyEntryData(long entryIndex, ByteBuf entryData, WriteFuture<?> future) {
        if (future != null) {
            future.notifySuccess(entryIndex);
        }
        entryData.release();
    }

}
