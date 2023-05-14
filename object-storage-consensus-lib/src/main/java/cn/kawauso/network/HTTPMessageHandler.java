package cn.kawauso.network;

import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.util.WriteFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import static io.netty.handler.codec.http.HttpHeaderNames.*;

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

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(CONTENT_LENGTH, 0);

        ctx.writeAndFlush(response);

        switch (request.uri()) {
            case "/":
                ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer();
                for (int i = 0; i < 32768; i++) {
                    byteBuf.writeByte(0);
                }

                log.info("开始写！");
                write(byteBuf);
                break;
            case "/debug":
                stateMachine.debug();
                break;
        }

        request.release();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    private void write(ByteBuf byteBuf) {
        WriteFuture<Long> future = stateMachine.writeToCluster(byteBuf.retain());
        future.onSuccess(result -> {
            log.info(result);
            write(byteBuf);
        });
    }

}
