package cn.kawauso;

import cn.kawauso.consensus.RaftEntryApplier;
import cn.kawauso.consensus.RaftStateMachine;
import cn.kawauso.consensus.DefaultRaftStateMachine;
import cn.kawauso.network.*;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.util.concurrent.EventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

/**
 * {@link RaftStateMachineStarter}是{@link RaftStateMachine}的启动辅助类
 *
 * @author RealDragonking
 */
@Service
public class RaftStateMachineStarter {

    private static final Logger log = LogManager.getLogger(RaftStateMachineStarter.class);

    /**
     * 初始化{@link UDPService}
     *
     * @param port 监听端口
     * @param ioThreads io线程数量
     * @return {@link UDPService}
     */
    @Bean(destroyMethod = "close")
    public UDPService initUDPService(@Value("${network.udp.port}") int port,
                                     @Value("${network.udp.io-threads}") int ioThreads) {
        if (Epoll.isAvailable()) {
            return new EpollUDPService(port, ioThreads);
        } else {
            return new GeneralUDPService(port);
        }
    }

    /**
     * 配置并初始化{@link RaftStateMachine}状态机内核，尝试启动相关的UDP网络通信服务
     *
     * @param udpService {@link UDPService}
     * @param execThreadGroup {@link ExecutorService}任务执行线程池
     * @param entryApplier {@link RaftEntryApplier}数据应用角色
     * @param index 当前节点在集群中的序列号
     * @param tickValue 每个时间单元
     * @param sendInterval 发送消息的时间间隔
     * @param minElectTimeout 最小的选举超时时间
     * @param maxElectTimeout 最大的选举超时时间
     * @param sendWindowSize Entry同步数据发送的时间间隔
     * @param allNodeAddresses 所有节点的地址列表
     *
     * @return {@link RaftStateMachine}
     */
    @Bean(destroyMethod = "close")
    public RaftStateMachine initNewStateMachine(UDPService udpService, EventExecutor execThreadGroup,
                                                RaftEntryApplier entryApplier,
                                                @Value("${raft.index}") int index,
                                                @Value("${raft.timer.tick-value}") int tickValue,
                                                @Value("${raft.timer.send-interval}") int sendInterval,
                                                @Value("${raft.timer.min-elect-timeout}") int minElectTimeout,
                                                @Value("${raft.timer.max-elect-timeout}") int maxElectTimeout,
                                                @Value("${raft.send-window-size}") int sendWindowSize,
                                                @Value("${raft.all-node-address}") String allNodeAddresses) {

        try {

            RaftStateMachine stateMachine = new DefaultRaftStateMachine(
                    udpService,
                    entryApplier,
                    index,
                    tickValue,
                    sendInterval,
                    minElectTimeout,
                    maxElectTimeout,
                    sendWindowSize,
                    allNodeAddresses.split(",")
            );

            ChannelInboundHandler handler = new UDPMessageHandler(stateMachine);

            udpService.setChannelInitializer(new ChannelInitializer<>() {
                @Override
                protected void initChannel(@NotNull DatagramChannel ch) {
                    ch.pipeline().addLast(execThreadGroup, handler);
                }
            });

            udpService.start();
            stateMachine.start();

            log.info("RaftStateMachine has started successfully !");
            log.info("UDP-Service: core={} io-threads={} port={}",
                    udpService.getName(), udpService.getIOThreads(), udpService.getPort());

            return stateMachine;
        } catch (Exception e) {
            log.info("RaftStateMachine started fail, error message:{}", e.toString());
            System.exit(1);
        }

        return null;
    }

}
