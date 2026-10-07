package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 18:28
 **/

public final class PaxosDemo {

    private static final Logger LOGGER = LogManager.getLogger(PaxosDemo.class);

    public static final com.google.common.hash.HashFunction HASH_FUNCTION = com.google.common.hash.Hashing.murmur3_32();
    private static final Random RANDOM = new Random();
    private static final String[] PROPOSALS = {"ProjectA", "ProjectB", "ProjectC"};

    public static void main(String[] args) {
        List<Acceptor> acceptors = new ArrayList<Acceptor>();
        Arrays.asList("A", "B", "C", "D", "E")
                .forEach(name -> acceptors.add(new Acceptor(name)));
        LOGGER.info("Paxos demo starting: 5 acceptors (A-E), initial proposal[1:null]");
        Proposer.vote(new Proposal(1L, null), acceptors);
    }

    /**
     * 对于提案的约束，第三条约束要求：
     * 如果maxVote不存在，那么没有限制，下一次表决可以使用任意提案；
     * 否则，下一次表决要沿用maxVote的提案
     *
     * @param currentVoteNumber
     * @param proposals
     * @return
     */
    public static Proposal nextProposal(long currentVoteNumber, List<Proposal> proposals) {
        long voteNumber = currentVoteNumber + 1;
        if (proposals.isEmpty()) {
            Proposal next = new Proposal(voteNumber, PROPOSALS[RANDOM.nextInt(PROPOSALS.length)]);
            LOGGER.debug("NEXT_PROPOSAL: no promised proposals, pick random -> [{}]", next);
            return next;
        }
        Collections.sort(proposals);
        Proposal maxVote = proposals.get(proposals.size() - 1);
        long maxVoteNumber = maxVote.getVoteNumber();
        String content = maxVote.getContent();
        if (maxVoteNumber >= currentVoteNumber)
            throw new IllegalStateException("illegal state maxVoteNumber");
        if (content != null) {
            Proposal next = new Proposal(voteNumber, content);
            LOGGER.debug("NEXT_PROPOSAL: reuse maxVote[{}] content -> [{}]", maxVote, next);
            return next;
        } else {
            Proposal next = new Proposal(voteNumber, PROPOSALS[RANDOM.nextInt(PROPOSALS.length)]);
            LOGGER.debug("NEXT_PROPOSAL: maxVote[{}] has no content, pick random -> [{}]", maxVote, next);
            return next;
        }
    }
}
