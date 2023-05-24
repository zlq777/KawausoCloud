package cn.kawauso;

import cn.kawauso.consensus.RaftEntryApplier;
import cn.kawauso.util.WriteFuture;
import io.netty.buffer.ByteBuf;
import org.springframework.stereotype.Component;

/**
 * {@link RaftEntryApplierImpl}实现了{@link RaftEntryApplier}
 *
 * @author RealDragonking
 */
@Component
public class RaftEntryApplierImpl implements RaftEntryApplier {

    public RaftEntryApplierImpl() {

    }

    /**
     * 将已经写入到集群大多数节点、当前节点可以应用的Entry数据进行应用
     *
     * @param entryIndex 可以应用的Entry序列号
     * @param entryTerm  可以应用的Entry所属任期
     * @param entryData  {@link ByteBuf}，可以应用的Entry数据
     * @param future     {@link WriteFuture}，可能为null，这取决于leader节点在处理用户写入请求时，是否发生过重启或者轮换。
     *                   对于follower节点来说，一般都会是null
     */
    @Override
    public void applyEntryData(long entryIndex, long entryTerm, ByteBuf entryData, WriteFuture<?> future) {

    }

}
