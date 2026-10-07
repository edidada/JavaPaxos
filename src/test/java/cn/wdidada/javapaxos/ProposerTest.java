package cn.wdidada.javapaxos;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Proposer 端到端共识测试：正常网络、故障网络、并发争抢安全性、网络全断超时。
 */
public class ProposerTest {

    private ExecutorService workers;

    @Before
    public void setUp() {
        workers = Proposer.newDaemonPool(16, "test-worker");
    }

    @After
    public void tearDown() {
        workers.shutdownNow();
    }

    private PaxosConfig fastConfig() {
        return PaxosConfig.defaults()
                .setFailureRate(0.0)
                .setMaxLatencyMillis(0)
                .setRpcTimeoutMillis(2000)
                .setBackoffBaseMillis(0)
                .setMaxRounds(200);
    }

    @Test
    public void decidesInSingleRoundWithReliableNetwork() throws Exception {
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        Proposer proposer = new Proposer("P0", cluster, workers, fastConfig(), new PaxosMetrics(),
                () -> "ProjectX");
        PaxosResult result = proposer.propose();
        assertNotNull(result);
        assertEquals(1, result.getRounds());
        assertEquals("ProjectX", result.getProposal().getContent());
        // 多数派已持久化决议
        int acceptedCount = 0;
        for (Acceptor acceptor : cluster) {
            if ("ProjectX".equals(acceptor.getAccepted().getContent())) {
                acceptedCount++;
            }
        }
        assertTrue(acceptedCount >= 3);
    }

    @Test(timeout = 60_000)
    public void decidesUnderFortyFivePercentFailures() {
        PaxosConfig config = fastConfig()
                .setFailureRate(0.45)
                .setBackoffBaseMillis(1)
                .setSeed(7);
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        Proposer proposer = new Proposer("P0", cluster, workers, config, new PaxosMetrics());
        PaxosResult result = proposer.propose();
        assertNotNull("must eventually decide", result);
        assertNotNull(result.getProposal().getContent());
        assertTrue(result.getRounds() > 1);
    }

    @Test(timeout = 120_000)
    public void concurrentProposersNeverDecideTwoValues() throws Exception {
        PaxosConfig config = fastConfig()
                .setFailureRate(0.2)
                .setBackoffBaseMillis(1)
                .setSeed(2026);
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        PaxosMetrics metrics = new PaxosMetrics();
        String[] values = {"V1", "V2", "V3", "V4"};
        List<Future<PaxosResult>> futures = new ArrayList<Future<PaxosResult>>();
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            Proposer proposer = new Proposer("P" + i, cluster, workers, config, metrics, () -> value);
            futures.add(workers.submit(proposer::propose));
        }
        Set<String> decided = new HashSet<String>();
        for (Future<PaxosResult> future : futures) {
            decided.add(future.get(60, TimeUnit.SECONDS).getProposal().getContent());
        }
        // 安全性：quorum 相交保证第一个被选定的值是唯一的
        assertEquals("safety violated, decided values: " + decided, 1, decided.size());
    }

    @Test(timeout = 60_000)
    public void learnsPreviouslyChosenValue() {
        PaxosConfig config = fastConfig();
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        // 先让一个提案在多数派上落地（模拟历史实例）
        Proposal committed = new Proposal(1, "LegacyValue");
        int n = 0;
        for (Acceptor acceptor : cluster) {
            if (n++ < 3) {
                acceptor.onPrepare(committed);
                acceptor.onAccept(committed);
            }
        }
        Proposer proposer = new Proposer("P0", cluster, workers, config, new PaxosMetrics(),
                () -> "Fresh");
        PaxosResult result = proposer.propose();
        // 新 Proposer 从 voteNumber=1 开始会收到 REJECTED -> 抬升投票号 -> prepare 承诺时
        // 带回 LegacyValue，必须沿用而非自由选取
        assertEquals("LegacyValue", result.getProposal().getContent());
    }

    @Test
    public void givesUpWhenNetworkIsDead() {
        PaxosConfig config = fastConfig()
                .setFailureRate(0.999)
                .setMaxRounds(3)
                .setSeed(3);
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        Proposer proposer = new Proposer("P0", cluster, workers, config, new PaxosMetrics());
        try {
            proposer.propose();
            fail("expected IllegalStateException when no quorum is reachable");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("rounds"));
        }
    }

    @Test(timeout = 60_000)
    public void metricsTrackRpcDropRate() {
        PaxosConfig config = fastConfig().setFailureRate(0.5).setSeed(9);
        List<Acceptor> cluster = PaxosDemo.buildCluster(5);
        PaxosMetrics metrics = new PaxosMetrics();
        new Proposer("P0", cluster, workers, config, metrics).propose();
        assertTrue(metrics.rpcSent() > metrics.rpcDropped());
        assertTrue(metrics.rpcDropped() > 0);
        assertEquals(1, metrics.decisions());
        assertTrue(metrics.snapshot().contains("decisions=1"));
    }
}
