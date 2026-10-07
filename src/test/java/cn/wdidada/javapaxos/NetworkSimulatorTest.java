package cn.wdidada.javapaxos;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * NetworkSimulator 故障注入行为测试。
 */
public class NetworkSimulatorTest {

    @Test
    public void totalFailureDropsEveryRpc() {
        PaxosConfig config = PaxosConfig.defaults()
                .setFailureRate(1.0)
                .setMaxLatencyMillis(0);
        NetworkSimulator network = new NetworkSimulator(config);
        AtomicInteger invoked = new AtomicInteger();
        Object result = network.call("A", () -> {
            invoked.incrementAndGet();
            return new Object();
        });
        assertNull(result);
        assertEquals(0, invoked.get());
    }

    @Test
    public void reliableNetworkPassesThrough() {
        PaxosConfig config = PaxosConfig.defaults()
                .setFailureRate(0.0)
                .setMaxLatencyMillis(0);
        NetworkSimulator network = new NetworkSimulator(config);
        Object payload = new Object();
        assertSame(payload, network.call("A", () -> payload));
    }

    @Test
    public void latencyStaysWithinBound() {
        PaxosConfig config = PaxosConfig.defaults()
                .setFailureRate(0.0)
                .setMaxLatencyMillis(5)
                .setSeed(42);
        NetworkSimulator network = new NetworkSimulator(config);
        long start = System.nanoTime();
        String result = network.call("A", () -> "ok");
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals("ok", result);
        assertTrue("latency " + millis + "ms should be within bound + slack", millis < 100);
    }

    @Test
    public void metricsCountSentAndDropped() {
        PaxosMetrics metrics = new PaxosMetrics();
        PaxosConfig config = PaxosConfig.defaults()
                .setFailureRate(1.0)
                .setMaxLatencyMillis(0)
                .setSeed(1);
        NetworkSimulator network = new NetworkSimulator(config, metrics);
        network.call("A", () -> "x");
        network.call("B", () -> "y");
        assertEquals(2, metrics.rpcSent());
        assertEquals(2, metrics.rpcDropped());
    }
}
