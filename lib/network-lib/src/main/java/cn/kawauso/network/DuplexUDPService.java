package cn.kawauso.network;

import io.netty.channel.socket.DatagramPacket;

/**
 * {@link DuplexUDPService}作为{@link NetworkService}的UDP协议接口拓展，提供了主动发送{@link io.netty.channel.socket.DatagramPacket}
 * 的能力
 *
 * @author RealDragonking
 */
public interface DuplexUDPService extends NetworkService {

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet {@link DatagramPacket}
     */
    void send(DatagramPacket packet);

}
