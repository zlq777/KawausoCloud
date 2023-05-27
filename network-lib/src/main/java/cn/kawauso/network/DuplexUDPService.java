package cn.kawauso.network;

import io.netty.channel.socket.DatagramPacket;

/**
 * {@link DuplexUDPService}作为{@link UDPService}的拓展，提供了主动发送{@link io.netty.channel.socket.DatagramPacket}
 * 的能力
 *
 * @author RealDragonking
 */
public interface DuplexUDPService extends UDPService {

    /**
     * 发送{@link DatagramPacket}数据包
     *
     * @param packet {@link DatagramPacket}
     */
    void send(DatagramPacket packet);

}
