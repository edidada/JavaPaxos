package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * 演示入口：一个集群上多个 Proposer 并发竞争同一实例，验证收敛与安全性。
 *
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 18:28
 **/

public final class PaxosDemo {

    private static final Logger LOGGER = LogManager.getLogger(PaxosDemo.class);

    public static void main(String[] args) throws Exception {
        PaxosConfig config = PaxosConfig.defaults();
        PaxosMetrics metrics = new PaxosMetrics();
        List<Acceptor> cluster = buildCluster(config.getAcceptorCount());
        int proposerCount = Integer.getInteger("paxos.proposers", 3);
        ExecutorService workers = Proposer.newDaemonPool(
                Integer.getInteger("paxos.workerThreads", 32), "paxos-worker");
        ExecutorService drivers = Proposer.newDaemonPool(
                Integer.getInteger("paxos.driverThreads",
                        Math.max(4, Runtime.getRuntime().availableProcessors())), "paxos-driver");
        try {
            LOGGER.info("Paxos demo: cluster={}, proposers={}, config={}",
                    cluster.size(), proposerCount, config);
            List<Future<PaxosResult>> futures = new ArrayList<Future<PaxosResult>>();
            for (int i = 0; i < proposerCount; i++) {
                Proposer proposer = new Proposer("P" + i, cluster, workers, config, metrics);
                futures.add(drivers.submit(proposer::propose));
            }
            Set<String> chosenValues = new HashSet<String>();
            for (Future<PaxosResult> future : futures) {
                PaxosResult result = future.get();
                LOGGER.info("RESULT {}", result);
                chosenValues.add(result.getProposal().getContent());
            }
            LOGGER.info("Safety check: distinct chosen values = {} (must be 1): {}",
                    chosenValues.size(), chosenValues);
            metrics.logSnapshot();
        } finally {
            drivers.shutdownNow();
            workers.shutdownNow();
        }
    }

    static List<Acceptor> buildCluster(int size) {
        List<Acceptor> cluster = new ArrayList<Acceptor>(size);
        for (int i = 0; i < size; i++) {
            cluster.add(new Acceptor(String.valueOf((char) ('A' + i % 26))));
        }
        return cluster;
    }
}
