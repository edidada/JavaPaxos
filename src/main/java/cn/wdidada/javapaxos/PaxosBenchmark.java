package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 工业级基准入口：
 * Phase 1 吞吐 —— 大量相互独立的 Paxos 实例并发决策，统计吞吐与 p50/p90/p99 决策延迟；
 * Phase 2 争抢 —— 单集群多 Proposer 竞争同一实例，验证安全性（唯一决议值）与活锁退避效果。
 *
 * 可调参数（系统属性）：
 * -Dpaxos.benchmark.instances=500      独立实例数
 * -Dpaxos.benchmark.contention=8       争抢阶段 Proposer 数
 * -Dpaxos.benchmark.threads=64         RPC 工作线程数
 *
 * @author: WuCheng
 **/
public final class PaxosBenchmark {

    private static final Logger LOGGER = LogManager.getLogger(PaxosBenchmark.class);

    public static void main(String[] args) throws Exception {
        PaxosConfig config = PaxosConfig.defaults();
        int instances = Integer.getInteger("paxos.benchmark.instances", 500);
        int contention = Integer.getInteger("paxos.benchmark.contention", 8);
        int threads = Integer.getInteger("paxos.benchmark.threads", 64);

        ExecutorService workers = Proposer.newDaemonPool(threads, "bench-worker");
        ExecutorService drivers = Proposer.newDaemonPool(
                Math.max(4, Runtime.getRuntime().availableProcessors() * 2), "bench-driver");

        LOGGER.info("=========== JavaPaxos benchmark ===========");
        LOGGER.info("config={}, instances={}, contention={}, workerThreads={}", config, instances, contention, threads);

        try {
            // ---------------- Phase 1: throughput ----------------
            PaxosMetrics throughputMetrics = new PaxosMetrics();
            List<Future<PaxosResult>> futures = new ArrayList<Future<PaxosResult>>(instances);
            for (int i = 0; i < instances; i++) {
                List<Acceptor> cluster = PaxosDemo.buildCluster(config.getAcceptorCount());
                Proposer proposer = new Proposer("bench-" + i, cluster, workers, config, throughputMetrics);
                futures.add(drivers.submit(proposer::propose));
            }
            long wallStart = System.nanoTime();
            int ok = 0;
            for (Future<PaxosResult> future : futures) {
                PaxosResult result = future.get();
                if (result != null && result.getProposal() != null) {
                    ok++;
                }
            }
            double wallMillis = (System.nanoTime() - wallStart) / 1_000_000.0;
            List<Double> latencies = throughputMetrics.latencySnapshotMillis();
            Collections.sort(latencies);
            double throughput = ok / (wallMillis / 1000.0);

            LOGGER.info("=========== Phase 1: throughput ===========");
            LOGGER.info("decisions={}/{}, wallMs={}, throughput={} decisions/s",
                    ok, instances, String.format("%.1f", wallMillis), String.format("%.1f", throughput));
            if (!latencies.isEmpty()) {
                LOGGER.info("decision latency ms: p50={}, p90={}, p99={}, max={}, avg={}",
                        String.format("%.3f", percentile(latencies, 50)),
                        String.format("%.3f", percentile(latencies, 90)),
                        String.format("%.3f", percentile(latencies, 99)),
                        String.format("%.3f", latencies.get(latencies.size() - 1)),
                        String.format("%.3f", average(latencies)));
            }
            throughputMetrics.logSnapshot();

            // ---------------- Phase 2: contention / safety ----------------
            LOGGER.info("=========== Phase 2: contention safety ===========");
            PaxosMetrics contentionMetrics = new PaxosMetrics();
            List<Acceptor> sharedCluster = PaxosDemo.buildCluster(config.getAcceptorCount());
            List<Future<PaxosResult>> race = new ArrayList<Future<PaxosResult>>(contention);
            for (int i = 0; i < contention; i++) {
                Proposer proposer = new Proposer("race-" + i, sharedCluster, workers, config, contentionMetrics);
                race.add(drivers.submit(proposer::propose));
            }
            Set<String> chosenValues = new HashSet<String>();
            long totalRounds = 0;
            for (Future<PaxosResult> future : race) {
                PaxosResult result = future.get();
                LOGGER.info("RACE RESULT {}", result);
                chosenValues.add(result.getProposal().getContent());
                totalRounds += result.getRounds();
            }
            for (Acceptor acceptor : sharedCluster) {
                LOGGER.debug("acceptor[{}] final accepted: {}", acceptor.getName(), acceptor.getAccepted());
            }
            LOGGER.info("safety: {} proposers decided on values {} -> {}",
                    contention, chosenValues, chosenValues.size() == 1 ? "PASS" : "FAIL");
            LOGGER.info("avg rounds per proposer under contention: {}",
                    String.format("%.2f", totalRounds / (double) contention));
            contentionMetrics.logSnapshot();

            if (chosenValues.size() != 1 || ok != instances) {
                throw new IllegalStateException("benchmark verification failed");
            }
            LOGGER.info("=========== benchmark complete: ALL PASS ===========");
        } finally {
            drivers.shutdownNow();
            workers.shutdownNow();
        }
    }

    static double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    static double average(List<Double> values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return values.isEmpty() ? 0 : sum / values.size();
    }
}
