package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Paxos Proposer：并行广播 PREPARE / ACCEPT，带 RPC 超时、quorum 判定、
 * 投票号学习（maxVoteSeen+1）与随机指数退避，防止多 Proposer 活锁。
 *
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:23
 **/

public class Proposer {

    private static final Logger LOGGER = LogManager.getLogger(Proposer.class);

    public static final String[] DEFAULT_PROPOSALS = {"ProjectA", "ProjectB", "ProjectC"};

    private final String name;
    private final List<Acceptor> acceptors;
    private final ExecutorService executor;
    private final PaxosConfig config;
    private final PaxosMetrics metrics;
    private final NetworkSimulator network;
    private final Supplier<String> freeChoice;

    public Proposer(String name, List<Acceptor> acceptors, ExecutorService executor,
                    PaxosConfig config, PaxosMetrics metrics) {
        this(name, acceptors, executor, config, metrics, randomFreeChoice());
    }

    public Proposer(String name, List<Acceptor> acceptors, ExecutorService executor,
                    PaxosConfig config, PaxosMetrics metrics, Supplier<String> freeChoice) {
        this.name = name;
        this.acceptors = acceptors;
        this.executor = executor;
        this.config = config;
        this.metrics = metrics;
        this.network = new NetworkSimulator(config, metrics);
        this.freeChoice = freeChoice;
    }

    /**
     * 运行一次单实例（single-decree）共识，阻塞直到形成决议或超出最大轮数。
     */
    public PaxosResult propose() {
        int quorum = Math.floorDiv(acceptors.size(), 2) + 1;
        long voteNumber = 1L;
        String learnedValue = null;
        long start = System.nanoTime();
        int round = 0;
        LOGGER.info("{}: propose start, acceptors={}, quorum={}, config={}", name, acceptors.size(), quorum, config);
        while (++round <= config.getMaxRounds()) {
            metrics.recordRound();

            // ---------- Phase 1: PREPARE ----------
            Proposal prepare = new Proposal(voteNumber, null);
            LOGGER.debug("{}: round[{}] PREPARE [{}]", name, round, prepare);
            List<Promise> promises = broadcast(
                    acceptor -> network.call(acceptor.getName(), () -> acceptor.onPrepare(prepare)));
            int acks = 0;
            long maxVoteSeen = voteNumber;
            Proposal maxAccepted = null;
            for (Promise promise : promises) {
                if (!promise.isAck()) {
                    continue;
                }
                acks++;
                Proposal last = promise.getProposal();
                if (last != null && last.getVoteNumber() > 0) {
                    if (maxAccepted == null || last.getVoteNumber() > maxAccepted.getVoteNumber()) {
                        maxAccepted = last;
                    }
                    maxVoteSeen = Math.max(maxVoteSeen, last.getVoteNumber());
                }
            }
            metrics.recordPrepareAcks(acks);
            if (acks < quorum) {
                LOGGER.debug("{}: round[{}] PREPARE quorum missed, acks={}/{}", name, round, acks, quorum);
                voteNumber = maxVoteSeen + 1;
                backoff(round);
                continue;
            }

            // 约束：沿用最高投票号的已接受提案内容；否则自由选取
            if (maxAccepted != null && maxAccepted.getContent() != null) {
                learnedValue = maxAccepted.getContent();
                LOGGER.debug("{}: round[{}] reuse learned content from maxAccepted[{}]", name, round, maxAccepted);
            }
            Proposal proposal = new Proposal(voteNumber,
                    learnedValue != null ? learnedValue : freeChoice.get());

            // ---------- Phase 2: ACCEPT ----------
            LOGGER.info("{}: round[{}] PREPARE ok {}/{} -> ACCEPT [{}]", name, round, acks, quorum, proposal);
            List<Boolean> accepts = broadcast(
                    acceptor -> network.call(acceptor.getName(), () -> acceptor.onAccept(proposal)));
            int acceptCount = 0;
            for (Boolean ack : accepts) {
                if (ack) {
                    acceptCount++;
                }
            }
            metrics.recordAcceptAcks(acceptCount);
            if (acceptCount >= quorum) {
                long latencyNanos = System.nanoTime() - start;
                metrics.recordDecision(latencyNanos);
                LOGGER.info("{}: DECIDED [{}] in round[{}], accepts={}/{}, latencyMs={}",
                        name, proposal, round, acceptCount, quorum, latencyNanos / 1_000_000.0);
                return new PaxosResult(name, proposal, round, latencyNanos);
            }
            LOGGER.debug("{}: round[{}] ACCEPT quorum missed, accepts={}/{}", name, round, acceptCount, quorum);
            voteNumber = Math.max(maxVoteSeen, voteNumber) + 1;
            backoff(round);
        }
        throw new IllegalStateException(
                name + ": failed to reach decision within " + config.getMaxRounds() + " rounds");
    }

    /**
     * 并行广播 RPC：提交到线程池，统一截止时间收结果；超时/失败按丢包处理。
     */
    private <T> List<T> broadcast(Function<Acceptor, T> rpc) {
        List<Future<T>> futures = new ArrayList<Future<T>>(acceptors.size());
        for (Acceptor acceptor : acceptors) {
            futures.add(executor.submit(() -> rpc.apply(acceptor)));
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(config.getRpcTimeoutMillis());
        List<T> responses = new ArrayList<T>();
        for (Future<T> future : futures) {
            try {
                long remaining = Math.max(1L, deadline - System.nanoTime());
                T response = future.get(remaining, TimeUnit.NANOSECONDS);
                if (response != null) {
                    responses.add(response);
                }
            } catch (TimeoutException e) {
                future.cancel(true);
                metrics.recordRpcTimeout();
                LOGGER.debug("{}: RPC timed out", name);
            } catch (ExecutionException e) {
                future.cancel(true);
                metrics.recordRpcTimeout();
                LOGGER.warn("{}: RPC execution error: {}", name, e.getCause(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                metrics.recordRpcTimeout();
                break;
            }
        }
        return responses;
    }

    /**
     * 随机指数退避（jittered backoff）：多 Proposer 冲突时打破活锁。
     */
    private void backoff(int round) {
        long base = config.getBackoffBaseMillis();
        if (base <= 0) {
            return;
        }
        long bound = Math.min(config.getBackoffCapMillis(), base << Math.min(round, 20));
        long sleepMillis = (long) (Math.random() * bound);
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 提案约束第三条（保留自原始实现，便于单独测试）：
     * 如果 maxVote 不存在，下一次表决可以使用任意提案；否则沿用 maxVote 的提案。
     *
     * @param currentVoteNumber 当前投票号
     * @param proposals         acceptors 回传的历史已接受提案
     * @return 下一次表决的提案
     */
    public static Proposal nextProposal(long currentVoteNumber, List<Proposal> proposals) {
        return nextProposal(currentVoteNumber, proposals, randomFreeChoice());
    }

    public static Proposal nextProposal(long currentVoteNumber, List<Proposal> proposals,
                                        Supplier<String> freeChoice) {
        long voteNumber = currentVoteNumber + 1;
        if (proposals.isEmpty()) {
            return new Proposal(voteNumber, freeChoice.get());
        }
        List<Proposal> sorted = new ArrayList<Proposal>(proposals);
        Collections.sort(sorted);
        Proposal maxVote = sorted.get(sorted.size() - 1);
        long maxVoteNumber = maxVote.getVoteNumber();
        String content = maxVote.getContent();
        if (maxVoteNumber >= currentVoteNumber) {
            throw new IllegalStateException("illegal state maxVoteNumber");
        }
        if (content != null) {
            return new Proposal(voteNumber, content);
        }
        return new Proposal(voteNumber, freeChoice.get());
    }

    private static Supplier<String> randomFreeChoice() {
        final Random random = new Random();
        return () -> DEFAULT_PROPOSALS[random.nextInt(DEFAULT_PROPOSALS.length)];
    }

    /**
     * 创建守护线程池，避免 JVM 无法正常退出。
     */
    public static ExecutorService newDaemonPool(int size, String prefix) {
        ThreadFactory factory = new ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger counter =
                    new java.util.concurrent.atomic.AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        return Executors.newFixedThreadPool(size, factory);
    }

    public String getName() {
        return name;
    }
}
