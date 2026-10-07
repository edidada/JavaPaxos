package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:21
 **/

public class Acceptor {

    private static final Logger LOGGER = LogManager.getLogger(Acceptor.class);

    //上次表决结果
    private Proposal last = new Proposal();
    private String name;

    public Acceptor(String name) {
        this.name = name;
    }

    public Promise onPrepare(Proposal proposal) {
        //假设这个过程有50%的几率失败
        if (Math.random() - 0.5 > 0) {
            LOGGER.debug("ACCEPTER_{}: PREPARE proposal[{}] -> NO RESPONSE", name, proposal);
            return null;
        }
        if (proposal == null)
            throw new IllegalArgumentException("null proposal");
        if (proposal.getVoteNumber() > last.getVoteNumber()) {
            Promise response = new Promise(true, last);
            last = proposal;
            LOGGER.debug("ACCEPTER_{}: PREPARE proposal[{}] -> OK (promised last accepted[{}])",
                    name, proposal, response.getProposal());
            return response;
        } else {
            LOGGER.debug("ACCEPTER_{}: PREPARE proposal[{}] -> REJECTED (already promised to voteNumber={})",
                    name, proposal, last.getVoteNumber());
            return new Promise(false, null);
        }
    }

    public boolean onAccept(Proposal proposal) {
        //假设这个过程有50%的几率失败
        if (Math.random() - 0.5 > 0) {
            LOGGER.debug("ACCEPTER_{}: ACCEPT proposal[{}] -> NO RESPONSE", name, proposal);
            return false;
        }
        boolean accepted = last.equals(proposal);
        LOGGER.debug("ACCEPTER_{}: ACCEPT proposal[{}] -> {}", name, proposal, accepted ? "OK" : "MISMATCH");
        return accepted;
    }
}
