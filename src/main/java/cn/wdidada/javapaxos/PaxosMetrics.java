package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 线程安全运行指标：RPC 收发、quorum 达成、表决轮数、决策延迟分布。
 *
 * @author: WuCheng
 **/
public final class PaxosMetrics {

    private static final Logger LOGGER = LogManager.getLogger(PaxosMetrics.class);

    private final LongAdder rpcSent = new LongAdder();
    private final LongAdder rpcDropped = new LongAdder();
    private final LongAdder rpcTimeouts = new LongAdder();
    private final LongAdder prepareAcks = new LongAdder();
    private final LongAdder acceptAcks = new LongAdder();
    private final LongAdder rounds = new LongAdder();
    private final LongAdder decisions = new LongAdder();
    private final AtomicLong totalLatencyNanos = new AtomicLong();
    private final ConcurrentLinkedQueue<Long> decisionLatencyNanos = new ConcurrentLinkedQueue<Long>();

    public void recordRpcSent() {
        rpcSent.increment();
    }

    public void recordRpcDropped() {
        rpcDropped.increment();
    }

    public void recordRpcTimeout() {
        rpcTimeouts.increment();
    }

    public void recordPrepareAcks(int n) {
        prepareAcks.add(n);
    }

    public void recordAcceptAcks(int n) {
        acceptAcks.add(n);
    }

    public void recordRound() {
        rounds.increment();
    }

    public void recordDecision(long latencyNanos) {
        decisions.increment();
        totalLatencyNanos.addAndGet(latencyNanos);
        decisionLatencyNanos.add(latencyNanos);
    }

    public long rpcSent() {
        return rpcSent.sum();
    }

    public long rpcDropped() {
        return rpcDropped.sum();
    }

    public long rpcTimeouts() {
        return rpcTimeouts.sum();
    }

    public long prepareAcks() {
        return prepareAcks.sum();
    }

    public long acceptAcks() {
        return acceptAcks.sum();
    }

    public long rounds() {
        return rounds.sum();
    }

    public long decisions() {
        return decisions.sum();
    }

    /**
     * 平均决策延迟（毫秒）。
     */
    public double avgDecisionLatencyMillis() {
        long n = decisions.sum();
        return n == 0 ? 0 : totalLatencyNanos.get() / (double) n / 1_000_000;
    }

    /**
     * 全部决策延迟（毫秒）快照，未排序。
     */
    public List<Double> latencySnapshotMillis() {
        List<Double> out = new ArrayList<Double>(decisionLatencyNanos.size());
        for (Long nanos : decisionLatencyNanos) {
            out.add(nanos / 1_000_000.0);
        }
        return out;
    }

    public void reset() {
        rpcSent.reset();
        rpcDropped.reset();
        rpcTimeouts.reset();
        prepareAcks.reset();
        acceptAcks.reset();
        rounds.reset();
        decisions.reset();
        totalLatencyNanos.set(0);
        decisionLatencyNanos.clear();
    }

    public String snapshot() {
        long sent = rpcSent.sum();
        long dropped = rpcDropped.sum();
        double dropRate = sent == 0 ? 0 : dropped * 100.0 / sent;
        long dec = decisions.sum();
        long rd = rounds.sum();
        return String.format(
                "rpcSent=%d, rpcDropped=%d(%.1f%%), rpcTimeouts=%d, prepareAcks=%d, acceptAcks=%d, "
                        + "rounds=%d, decisions=%d, avgRoundsPerDecision=%.2f, avgDecisionLatencyMs=%.3f",
                sent, dropped, dropRate, rpcTimeouts.sum(), prepareAcks.sum(), acceptAcks.sum(),
                rd, dec, dec == 0 ? 0 : rd / (double) dec, avgDecisionLatencyMillis());
    }

    public void logSnapshot() {
        LOGGER.info("METRICS snapshot: {}", snapshot());
    }
}
