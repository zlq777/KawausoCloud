package cn.kawauso;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwt.interfaces.JWTVerifier;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.lang.NonNull;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;

/**
 * {@link HTTPRequestHandler}实现了{@link ChannelInboundHandler}，负责进行http报文的处理
 *
 * @author RealDragonking
 */
public final class HTTPRequestHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LogManager.getLogger(HTTPRequestHandler.class);
    private static final String STATE_CODE = "state-code";
    private static final String ERROR_MSG = "error-msg";
    private static final int ERROR_STATE_CODE = 0;
    private final JWTVerifier tokenVerifier;

    public HTTPRequestHandler(JWTVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
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

        FullHttpRequest request = (FullHttpRequest) msg;
        JSONObject requestBody = JSON.parseObject(request.content().array());

        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        JSONObject responseBody = new JSONObject();

        try {

            switch (request.uri()) {

                case "/bucket/create":
                    break;

                case "/bucket/delete":
                    break;

                case "/bucket/rename":
                    break;

                case "/bucket/list":
                    break;

                case "/directory/create":
                    break;

                case "/directory/delete":
                    break;

                case "/directory/rename":
                    break;

                case "/directory/list":
                    break;

                case "/object/create":
                    break;

                case "/object/delete":
                    break;

                case "/object/rename":
                    break;

                default:
                    throw new Exception("This is an invalid url location !");

            }

        } catch (Exception e) {
            responseBody.put(STATE_CODE, ERROR_STATE_CODE);
            responseBody.put(ERROR_MSG, e.getMessage());
        }

        byte[] responseBodyBytes = JSON.toJSONBytes(responseBody);

        response.headers().set(CONTENT_LENGTH, responseBodyBytes.length);
        response.content().writeBytes(responseBodyBytes);

        ctx.writeAndFlush(response);

        request.release();
    }

    /**
     * 在用户名下创建一个存储桶，并初始化设置相关参数
     *
     * @param userId 用户id
     * @param bucketName 存储桶名称
     * @param publicRead 是否允许公开读
     * @param publicWrite 是否允许公开写
     * @param response {@link FullHttpResponse}响应
     */
    private void createBucket(String userId,
                              String bucketName, boolean publicRead, boolean publicWrite,
                              FullHttpResponse response) {
        //
    }

}
