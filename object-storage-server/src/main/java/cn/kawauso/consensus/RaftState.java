package cn.kawauso.consensus;

/**
 * {@link RaftState}枚举了{@link RaftStateMachine}运行过程中的几种状态
 *
 * @author RealDragonking
 */
public enum RaftState {

    LEADER, FOLLOWER, CANDIDATE

}
