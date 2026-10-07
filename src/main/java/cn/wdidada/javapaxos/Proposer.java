package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:23
 **/

public class Proposer {

    private static final Logger LOGGER = LogManager.getLogger(Proposer.class);

    /**
     * @param proposal
     * @param acceptors
     */
    public static void vote(Proposal proposal, Collection<Acceptor> acceptors) {
        int quorum = Math.floorDiv(acceptors.size(), 2) + 1;
        LOGGER.info("VOTE start: proposal[{}], acceptors={}, quorum={}", proposal, acceptors.size(), quorum);
        int count = 0;
        while (true) {
            LOGGER.info("VOTE_ROUND[{}] start, proposing[{}]", ++count, proposal);
            List<Proposal> proposals = new ArrayList<Proposal>();
            for (Acceptor acceptor : acceptors) {
                Promise promise = acceptor.onPrepare(proposal);
                if (promise != null && promise.isAck())
                    proposals.add(promise.getProposal());
            }
            if (proposals.size() < quorum) {
                LOGGER.warn("PROPOSER: PREPARE failed, promises={}/{} for proposal[{}]",
                        proposals.size(), quorum, proposal);
                proposal = PaxosDemo.nextProposal(proposal.getVoteNumber(), proposals);
                LOGGER.info("PROPOSER: re-propose after prepare failure -> [{}]", proposal);
                continue;
            }
            LOGGER.info("PROPOSER: PREPARE ok, {} acceptors promised for proposal[{}]", proposals.size(), proposal);
            int acceptCount = 0;
            for (Acceptor acceptor : acceptors) {
                if (acceptor.onAccept(proposal))
                    acceptCount++;
            }
            if (acceptCount < quorum) {
                LOGGER.warn("PROPOSER: ACCEPT failed, acceptCount={}/{} for proposal[{}]",
                        acceptCount, quorum, proposal);
                proposal = PaxosDemo.nextProposal(proposal.getVoteNumber(), proposals);
                LOGGER.info("PROPOSER: re-propose after accept failure -> [{}]", proposal);
                continue;
            }
            break;
        }
        LOGGER.info("VOTE SUCCESS: proposal[{}] accepted by {} acceptors (quorum={}) after {} round(s)",
                proposal, quorum, quorum, count);
    }
}
