package cn.wdidada.javapaxos;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:21
 **/

public class Promise {

    private final boolean ack;
    private final Proposal proposal;

    public Promise(boolean ack, Proposal proposal) {
        this.ack = ack;
        this.proposal = proposal;
    }

    public boolean isAck() {
        return ack;
    }

    public Proposal getProposal() {
        return proposal;
    }

    @Override
    public String toString() {
        return "Promise{ack=" + ack + ", proposal=" + proposal + '}';
    }
}
