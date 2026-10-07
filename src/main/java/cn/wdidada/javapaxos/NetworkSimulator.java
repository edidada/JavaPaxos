package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.function.Supplier;

/**
 * 网络故障注入器：模拟 RPC 丢包（概率性失败）与随机延迟，用于对 Proposer 的
 * 超时/重试/退避路径做可复现的压力测试。failureRate=0 且 maxLatency=0 时退化为直通。
 *
 * @author: WuCheng
 **/
public final class NetworkSimulator {

    private static final Logger LOGGER = LogManager.getLogger(NetworkSimulator.class);

    private final double failureRate;
    private final long maxLatencyMillis;
    private final Random random;
    private final PaxosMetrics metrics;

    public NetworkSimulator(PaxosConfig config) {
        this(config, null);
    }

    public NetworkSimulator(PaxosConfig config, PaxosMetrics metrics) {
        this.failureRate = config.getFailureRate();
        this.maxLatencyMillis = config.getMaxLatencyMillis();
        this.metrics = metrics;
        this.random = config.getSeed() >= 0 ? new Random(config.getSeed()) : new Random();
    }

    /**
     * 模拟一次 RPC 调用；被丢弃时返回 null 且不会执行 rpc。
     */
    public <T> T call(String target, Supplier<T> rpc) {
        if (metrics != null) {
            metrics.recordRpcSent();
        }
        if (failureRate > 0 && random.nextDouble() < failureRate) {
            if (metrics != null) {
                metrics.recordRpcDropped();
            }
            LOGGER.trace("RPC to {} dropped by network simulation", target);
            return null;
        }
        if (maxLatencyMillis > 0) {
            long latency = (long) (random.nextDouble() * maxLatencyMillis);
            if (latency > 0) {
                try {
                    Thread.sleep(latency);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return rpc.get();
    }
}
