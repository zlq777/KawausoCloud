package cn.kawauso;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import org.springframework.lang.NonNull;

/**
 * {@link HTTPRequestHandler}实现了{@link ChannelInboundHandler}，负责进行http报文的处理
 *
 * @author RealDragonking
 */
public final class HTTPRequestHandler extends ChannelInboundHandlerAdapter {

    public HTTPRequestHandler() {
        //
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx {@link ChannelHandlerContext}
     * @param msg 数据
     */
    @Override
    public void channelRead(@NonNull ChannelHandlerContext ctx, @NonNull Object msg) {
        //
    }

}
