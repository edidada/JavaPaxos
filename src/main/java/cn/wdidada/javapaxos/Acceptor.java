package cn.wdidada.javapaxos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Paxos Acceptor：线程安全，维护 promised / accepted 两份状态，
 * 按经典协议语义处理 PREPARE 与 ACCEPT。
 *
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:21
 **/

public class Acceptor {

    private static final Logger LOGGER = LogManager.getLogger(Acceptor.class);

    //已经承诺的最高投票号
    private Proposal promised = new Proposal();
    //上次已接受（相当于已提交）的提案
    private Proposal accepted = new Proposal();

    private final String name;

    public Acceptor(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * PREPARE 阶段：投票号严格大于已承诺投票号才承诺，并回传上一份已接受提案。
     */
    public synchronized Promise onPrepare(Proposal proposal) {
        if (proposal == null)
            throw new IllegalArgumentException("null proposal");
        if (proposal.getVoteNumber() > promised.getVoteNumber()) {
            Promise response = new Promise(true, accepted);
            promised = proposal;
            LOGGER.debug("ACCEPTER_{}: PREPARE proposal[{}] -> OK (promised last accepted[{}])",
                    name, proposal, accepted);
            return response;
        }
        LOGGER.debug("ACCEPTER_{}: PREPARE proposal[{}] -> REJECTED (already promised voteNumber={})",
                name, proposal, promised.getVoteNumber());
        return new Promise(false, null);
    }

    /**
     * ACCEPT 阶段：投票号不低于已承诺投票号即可接受（经典 Paxos 语义）。
     */
    public synchronized boolean onAccept(Proposal proposal) {
        if (proposal == null)
            throw new IllegalArgumentException("null proposal");
        if (proposal.getVoteNumber() < promised.getVoteNumber()) {
            LOGGER.debug("ACCEPTER_{}: ACCEPT proposal[{}] -> REJECTED (promised voteNumber={})",
                    name, proposal, promised.getVoteNumber());
            return false;
        }
        accepted = proposal;
        LOGGER.debug("ACCEPTER_{}: ACCEPT proposal[{}] -> OK", name, proposal);
        return true;
    }

    /**
     * Learner 视图：当前已接受的提案。
     */
    public synchronized Proposal getAccepted() {
        return accepted;
    }
}
