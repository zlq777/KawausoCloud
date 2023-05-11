package cn.kawauso.network;

import cn.kawauso.main.RaftStateMachine;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * {@link HTTPMessageHandler}作为{@link ChannelInboundHandlerAdapter}的子类，
 * 能够被添加进{@link io.netty.channel.ChannelPipeline}中，处理TCP通信中的http报文。
 *
 * @author RealDragonking
 */
public final class HTTPMessageHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LogManager.getLogger(HTTPMessageHandler.class);
    private final RaftStateMachine stateMachine;

    public HTTPMessageHandler(RaftStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
        FullHttpRequest request = (FullHttpRequest) msg;

        RaftStateMachine.WriteFuture writeFuture = new RaftStateMachine.WriteFuture() {
            @Override
            public void onSuccess() {

            }

            @Override
            public void onCancelled() {

            }
        };

        if (! stateMachine.writeToCluster(request.content(), writeFuture)) {
            request.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

}
