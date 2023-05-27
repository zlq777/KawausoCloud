package cn.kawauso.consensus;

import cn.kawauso.network.DuplexUDPService;
import cn.kawauso.network.EpollUDPService;
import cn.kawauso.network.GeneralUDPService;
import cn.kawauso.network.UDPMessageHandler;
import cn.kawauso.util.WriteFuture;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.SystemPropertyUtil;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteOptions;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;

/**
 * {@link DefaultRaftStateMachine}实现了{@link AbstractRaftStateMachine}所需的数据读写层相关基础设施方法
 *
 * @author RealDragonking
 */
@Service
public final class DefaultRaftStateMachine extends AbstractRaftStateMachine {

    private final FileChannel entryDataFileChannel;
    private final RaftEntryApplier entryApplier;
    private final EventExecutor execThreadGroup;
    private final DuplexUDPService udpService;
    private final ByteBufAllocator allocator;
    private final WriteOptions writeOptions;
    private final ByteBuffer readBuffer;
    private final DB kvStore;

    /**
     * 配置并初始化{@link DefaultRaftStateMachine}状态机内核
     *
     * @param execThreadGroup {@link ExecutorService}任务执行线程池
     * @param entryApplier {@link RaftEntryApplier}数据应用角色
     * @param port udp服务监听的端口
     * @param ioThreads io线程数量
     * @param tickValue 每个时间单元
     * @param sendInterval 发送消息的时间间隔
     * @param minElectTimeout 最小的选举超时时间
     * @param maxElectTimeout 最大的选举超时时间
     * @param sendWindowSize Entry同步数据发送的时间间隔
     * @param index 当前节点在集群中的序列号
     * @param allNodeAddresses 所有节点的地址列表
     */
    public DefaultRaftStateMachine(EventExecutor execThreadGroup, RaftEntryApplier entryApplier,

                                    @Value("${network.udp.port}") int port,
                                    @Value("${network.udp.io-threads}") int ioThreads,

                                    @Value("${raft.timer.tick-value}") int tickValue,
                                    @Value("${raft.timer.send-interval}") int sendInterval,
                                    @Value("${raft.timer.min-elect-timeout}") int minElectTimeout,
                                    @Value("${raft.timer.max-elect-timeout}") int maxElectTimeout,
                                    @Value("${raft.send-window-size}") int sendWindowSize,

                                    @Value("${raft.index}") int index,
                                    @Value("${raft.all-node-address}") String allNodeAddresses) throws Exception {

        super(index, tickValue, sendInterval, minElectTimeout, maxElectTimeout, sendWindowSize, allNodeAddresses.split(","));

        this.kvStore = initKVStore();
        this.entryDataFileChannel = initEntryDataFileChannel();

        this.writeOptions = new WriteOptions().sync(false);

        this.readBuffer = ByteBuffer.allocate(MAX_ENTRY_SIZE);
        this.allocator = ByteBufAllocator.DEFAULT;

        ChannelInitializer<DatagramChannel> initializer = initChannelInitializer();
        DuplexUDPService udpService;

        if (Epoll.isAvailable()) {
            udpService = new EpollUDPService(ioThreads, port, initializer);
        } else {
            udpService = new GeneralUDPService(port, initializer);
        }

        this.execThreadGroup = execThreadGroup;
        this.entryApplier = entryApplier;
        this.udpService = udpService;

        this.start();
    }

    /**
     * 尝试初始化用于KV数据项读写的{@link DB}
     *
     * @return {@link DB}
     * @throws Exception 在初始化过程中可能会出现的异常
     */
    private DB initKVStore() throws Exception {
        String parentPath = SystemPropertyUtil.get("user.dir");
        File file = new File(parentPath, "data");

        Options options = new Options()
                .createIfMissing(true);

        return Iq80DBFactory.factory.open(file, options);
    }

    /**
     * 尝试初始化用于EntryData读写的{@link FileChannel}
     *
     * @return {@link FileChannel}
     * @throws Exception 在初始化过程中可能会出现的异常
     */
    private FileChannel initEntryDataFileChannel() throws Exception {
        String parentPath = SystemPropertyUtil.get("user.dir");
        Path path = Path.of(parentPath, "data", "entry-data");

        OpenOption[] options = new OpenOption[] {
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.READ
        };

        return FileChannel.open(path, options);
    }

    /**
     * 创建用于{@link DuplexUDPService}初始化的{@link ChannelInitializer}
     *
     * @return {@link ChannelInitializer}
     */
    private ChannelInitializer<DatagramChannel> initChannelInitializer() {
        ChannelInboundHandler handler = new UDPMessageHandler(this);
        return new ChannelInitializer<>() {
            @Override
            protected void initChannel(@NotNull DatagramChannel ch) {
                ch.pipeline().addLast(execThreadGroup, handler);
            }
        };
    }

    /**
     * 使用UDP协议，向指定的{@link InetSocketAddress}发送{@link ByteBuf}中已经写入的数据
     *
     * @param address {@link InetSocketAddress}
     * @param byteBuf {@link ByteBuf}
     */
    @Override
    protected void sendDatagramPacket(InetSocketAddress address, ByteBuf byteBuf) {
        DatagramPacket packet = new DatagramPacket(byteBuf, address);
        udpService.send(packet);
    }

    /**
     * 使用一定的分配策略，分配一个用于读写的{@link ByteBuf}字节缓冲区
     *
     * @return {@link ByteBuf}
     */
    @Override
    protected ByteBuf allocByteBuf() {
        return allocator.buffer();
    }

    /**
     * 从本地缓存中读取long类型的数据项，如果不存在则写入defaultValue
     *
     * @param key 数据项的键
     * @return 数据项的值
     */
    @Override
    protected long readLong(String key) {
        byte[] bytes = kvStore.get(key.getBytes(StandardCharsets.UTF_8));

        if (bytes == null) {
            writeLong(key, 0L);
            return 0L;
        } else {
            return (((long) bytes[0] & 0xff) << 56)
                    | (((long) bytes[1] & 0xff) << 48)
                    | (((long) bytes[2] & 0xff) << 40)
                    | (((long) bytes[3] & 0xff) << 32)
                    | (((long) bytes[4] & 0xff) << 24)
                    | (((long) bytes[5] & 0xff) << 16)
                    | (((long) bytes[6] & 0xff) << 8)
                    | (((long) bytes[7] & 0xff));
        }
    }

    /**
     * 向本地缓存中写入long类型的数据项
     *
     * @param key   数据项的键
     * @param value 数据项的值
     */
    @Override
    protected void writeLong(String key, long value) {
        byte[] bytes = new byte[8];

        bytes[0] = (byte) (value >> 56 & 0xff);
        bytes[1] = (byte) (value >> 48 & 0xff);
        bytes[2] = (byte) (value >> 40 & 0xff);
        bytes[3] = (byte) (value >> 32 & 0xff);
        bytes[4] = (byte) (value >> 24 & 0xff);
        bytes[5] = (byte) (value >> 16 & 0xff);
        bytes[6] = (byte) (value >> 8  & 0xff);
        bytes[7] = (byte) (value & 0xff);

        kvStore.put(key.getBytes(StandardCharsets.UTF_8), bytes, writeOptions);
    }

    /**
     * 从本地缓存中读取int类型的数据项
     *
     * @param key 数据项的键
     * @return 数据项的值，如果不存在则默认返回 false
     */
    @Override
    protected int readInt(String key) {
        byte[] bytes = kvStore.get(key.getBytes(StandardCharsets.UTF_8));

        if (bytes == null) {
            writeInt(key, -1);
            return -1;
        } else {
            return ((((int) bytes[0] & 0xff) << 24)
                    | (((int) bytes[1] & 0xff) << 16)
                    | (((int) bytes[2] & 0xff) << 8)
                    | (((int) bytes[3] & 0xff)));
        }
    }

    /**
     * 向本地缓存中写入int类型的数据项
     *
     * @param key   数据项的键
     * @param value 数据项的值
     */
    @Override
    protected void writeInt(String key, int value) {
        byte[] bytes = new byte[4];

        bytes[0] = (byte) (value >> 24 & 0xff);
        bytes[1] = (byte) (value >> 16 & 0xff);
        bytes[2] = (byte) (value >> 8  & 0xff);
        bytes[3] = (byte) (value & 0xff);

        kvStore.put(key.getBytes(StandardCharsets.UTF_8), bytes, writeOptions);
    }

    /**
     * 按照给定的Entry序列号，从本地缓存中读取Entry的所属任期和实际数据，并写入{@link ByteBuf}中
     *
     * @param entryIndex Entry的序列号
     * @return {@link Entry}
     */
    @Override
    protected Entry readEntry(long entryIndex) {
        long position = readLong(entryIndex + "pos");
        long length = readLong(entryIndex + "len");
        long term = readLong(entryIndex + "term");

        ByteBuffer readBuffer = this.readBuffer.position(0);
        ByteBuf byteBuf = allocator.buffer();

        try {
            entryDataFileChannel.position(position).read(readBuffer, length);
            byteBuf.writeBytes(readBuffer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new Entry(term, byteBuf, null);
    }

    /**
     * 向本地缓存中写入Entry的数据，这不需要增加{@link ByteBuf}的读指针位置
     *
     * @param entryIndex Entry的序列号
     * @param entryTerm  Entry的所属任期
     * @param entryData  Entry的数据
     */
    @Override
    protected void writeEntry(long entryIndex, long entryTerm, ByteBuf entryData) {
        ByteBuffer byteBuffer = entryData.nioBuffer();

        long prevEntryIndex = entryIndex - 1;
        long position = readLong(prevEntryIndex + "pos") + readLong(prevEntryIndex + "len");
        long length = byteBuffer.limit();

        long writePos = position;

        try {
            while (byteBuffer.hasRemaining()) {
                writePos += entryDataFileChannel.write(byteBuffer, writePos);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        writeLong(entryIndex + "pos", position);
        writeLong(entryIndex + "len", length);
        writeLong(entryIndex + "term", entryTerm);
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
        entryApplier.applyEntryData(entryIndex, entryTerm, entryData, future);
    }

    /**
     * 启动此{@link RaftStateMachine}的进程
     *
     * @throws Exception 启动过程中出现的异常
     */
    @Override
    public void start() throws Exception {
        super.start();

        udpService.start();

        log.info("RaftStateMachine has started successfully !");
        log.info("UDP-Service: core={} io-threads={} port={}",
                udpService.getName(), udpService.getIOThreads(), udpService.getPort());
    }

    /**
     * 关闭此{@link RaftStateMachine}的进程
     *
     * @throws Exception 关闭过程中出现的异常
     */
    @Override
    public void close() throws Exception {
        super.close();
        kvStore.close();
        udpService.close();
        entryDataFileChannel.close();
    }

}
