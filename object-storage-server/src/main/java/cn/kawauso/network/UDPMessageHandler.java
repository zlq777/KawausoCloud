package cn.kawauso.network;

import cn.kawauso.consensus.RaftStateMachine;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import org.jetbrains.annotations.NotNull;

import static cn.kawauso.consensus.RaftMessageType.*;

/**
 * <p>{@link UDPMessageHandler}作为{@link ChannelInboundHandlerAdapter}的子类，能够被添加进
 * {@link ChannelPipeline}中，处理UDP通信中的{@link DatagramPacket}类型的消息。</p>
 * {@link UDPMessageHandler}在服务节点进程中负责Raft算法的通信机制运转，接收来自其它节点的消息，反序列化后输入{@link RaftStateMachine}
 * 状态机当中。
 *
 * @author RealDragonking
 */
@ChannelHandler.Sharable
public final class UDPMessageHandler extends ChannelInboundHandlerAdapter {

    private final RaftStateMachine stateMachine;

    public UDPMessageHandler(RaftStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param msg {@link java.net.DatagramPacket}
     */
    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
        DatagramPacket packet = (DatagramPacket) msg;
        ByteBuf byteBuf = packet.content();

        if (stateMachine.isRunning()) {

            switch (byteBuf.readInt()) {

                case VOTE_REQUEST:
                    stateMachine.recvVoteRequest(
                            byteBuf.readInt(),
                            byteBuf.readLong(),
                            byteBuf.readLong(),
                            byteBuf.readLong(),
                            byteBuf.readBoolean(),
                            byteBuf.readLong()
                    );
                    byteBuf.release();
                    break;

                case VOTE_RESPONSE:
                    stateMachine.recvVoteResponse(
                            byteBuf.readLong(),
                            byteBuf.readBoolean(),
                            byteBuf.readBoolean(),
                            byteBuf.readBoolean(),
                            byteBuf.readLong()
                    );
                    byteBuf.release();
                    break;

                case LEADER_MESSAGE:
                    stateMachine.recvMessageFromLeader(
                            byteBuf.readInt(),
                            byteBuf.readLong(),
                            byteBuf.readLong(),
                            byteBuf.readableBytes() > 0 ? byteBuf.readLong() : 0L,
                            byteBuf
                    );
                    break;

                case FOLLOWER_RESPONSE:
                    stateMachine.recvResponseFromOther(
                            byteBuf.readInt(),
                            byteBuf.readLong(),
                            byteBuf.readLong()
                    );
                    byteBuf.release();
                    break;
            }

        } else {
            byteBuf.release();
        }
    }

}
