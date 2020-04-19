package paxos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:23
 **/

public class Proposer {

    /**
     * @param proposal
     * @param acceptors
     */
    public static void vote(Proposal proposal, Collection<Acceptor> acceptors) {
        int quorum = Math.floorDiv(acceptors.size(), 2) + 1;
        int count = 0;
        while (true) {
            printInfo("VOTE_ROUND", "START", ++count + "");
            List<Proposal> proposals = new ArrayList<Proposal>();
            for (Acceptor acceptor : acceptors) {
                Promise promise = acceptor.onPrepare(proposal);
                if (promise != null && promise.isAck())
                    proposals.add(promise.getProposal());
            }
            if (proposals.size() < quorum) {
                printInfo("PROPOSER[" + proposal + "]", "VOTE", "NOT PREPARED");
                proposal = PaxosDemo.nextProposal(proposal.getVoteNumber(), proposals);
                continue;
            }
            int acceptCount = 0;
            for (Acceptor acceptor : acceptors) {
                if (acceptor.onAccept(proposal))
                    acceptCount++;
            }
            if (acceptCount < quorum) {
                printInfo("PROPOSER[" + proposal + "]", "VOTE", "NOT ACCEPTED");
                proposal = PaxosDemo.nextProposal(proposal.getVoteNumber(), proposals);
                continue;
            }
            break;
        }
        printInfo("PROPOSER[" + proposal + "]", "VOTE", "SUCCESS");
    }

    public static void printInfo(String subject, String operation, String result) {
        System.out.println(subject + ":" + operation + "<" + result + ">");
    }
}

