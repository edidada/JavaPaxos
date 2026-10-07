package cn.wdidada.javapaxos;

/**
 * 集群与运行参数配置。所有字段默认从系统属性读取，便于不改代码调参：
 * -Dpaxos.acceptors / -Dpaxos.failureRate / -Dpaxos.maxLatencyMillis /
 * -Dpaxos.rpcTimeoutMillis / -Dpaxos.backoffBaseMillis / -Dpaxos.backoffCapMillis /
 * -Dpaxos.maxRounds / -Dpaxos.seed
 *
 * @author: WuCheng
 **/
public final class PaxosConfig {

    private int acceptorCount = Integer.getInteger("paxos.acceptors", 5);
    private double failureRate = Double.parseDouble(System.getProperty("paxos.failureRate", "0.2"));
    private long maxLatencyMillis = Long.getLong("paxos.maxLatencyMillis", 2L);
    private long rpcTimeoutMillis = Long.getLong("paxos.rpcTimeoutMillis", 2000L);
    private long backoffBaseMillis = Long.getLong("paxos.backoffBaseMillis", 1L);
    private long backoffCapMillis = Long.getLong("paxos.backoffCapMillis", 50L);
    private int maxRounds = Integer.getInteger("paxos.maxRounds", 10000);
    private long seed = Long.getLong("paxos.seed", -1L);

    public static PaxosConfig defaults() {
        return new PaxosConfig();
    }

    public int getAcceptorCount() {
        return acceptorCount;
    }

    public PaxosConfig setAcceptorCount(int acceptorCount) {
        this.acceptorCount = acceptorCount;
        return this;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public PaxosConfig setFailureRate(double failureRate) {
        this.failureRate = failureRate;
        return this;
    }

    public long getMaxLatencyMillis() {
        return maxLatencyMillis;
    }

    public PaxosConfig setMaxLatencyMillis(long maxLatencyMillis) {
        this.maxLatencyMillis = maxLatencyMillis;
        return this;
    }

    public long getRpcTimeoutMillis() {
        return rpcTimeoutMillis;
    }

    public PaxosConfig setRpcTimeoutMillis(long rpcTimeoutMillis) {
        this.rpcTimeoutMillis = rpcTimeoutMillis;
        return this;
    }

    public long getBackoffBaseMillis() {
        return backoffBaseMillis;
    }

    public PaxosConfig setBackoffBaseMillis(long backoffBaseMillis) {
        this.backoffBaseMillis = backoffBaseMillis;
        return this;
    }

    public long getBackoffCapMillis() {
        return backoffCapMillis;
    }

    public PaxosConfig setBackoffCapMillis(long backoffCapMillis) {
        this.backoffCapMillis = backoffCapMillis;
        return this;
    }

    public int getMaxRounds() {
        return maxRounds;
    }

    public PaxosConfig setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
        return this;
    }

    public long getSeed() {
        return seed;
    }

    public PaxosConfig setSeed(long seed) {
        this.seed = seed;
        return this;
    }

    @Override
    public String toString() {
        return "PaxosConfig{acceptors=" + acceptorCount
                + ", failureRate=" + failureRate
                + ", maxLatencyMs=" + maxLatencyMillis
                + ", rpcTimeoutMs=" + rpcTimeoutMillis
                + ", backoffBaseMs=" + backoffBaseMillis
                + ", backoffCapMs=" + backoffCapMillis
                + ", maxRounds=" + maxRounds
                + ", seed=" + seed + '}';
    }
}
