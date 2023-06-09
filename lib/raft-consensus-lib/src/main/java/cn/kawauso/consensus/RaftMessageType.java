package cn.kawauso.consensus;

/**
 * {@link RaftMessageType}是一个常量类，提供了{@link RaftStateMachine}需要处理的消息类型int字段
 *
 * @author RealDragonking
 */
public final class RaftMessageType {

    public static final int VOTE_REQUEST = 0;
    public static final int VOTE_RESPONSE = 1;
    public static final int HIGHER_TERM_NOTIFY = 2;
    public static final int LEADER_MESSAGE = 3;
    public static final int FOLLOWER_RESPONSE = 4;

}
