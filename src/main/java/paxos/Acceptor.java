package paxos;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:21
 **/

public class Acceptor {

    //上次表决结果
    private Proposal last = new Proposal();
    private String name;

    public Acceptor(String name) {
        this.name = name;
    }

    public Promise onPrepare(Proposal proposal) {
        //假设这个过程有50%的几率失败
        if (Math.random() - 0.5 > 0) {
            Proposer.printInfo("ACCEPTER_" + name, "PREPARE", "NO RESPONSE");
            return null;
        }
        if (proposal == null)
            throw new IllegalArgumentException("null proposal");
        if (proposal.getVoteNumber() > last.getVoteNumber()) {
            Promise response = new Promise(true, last);
            last = proposal;
            Proposer. printInfo("ACCEPTER_" + name, "PREPARE", "OK");
            return response;
        } else {
            Proposer.printInfo("ACCEPTER_" + name, "PREPARE", "REJECTED");
            return new Promise(false, null);
        }
    }

    public boolean onAccept(Proposal proposal) {
        //假设这个过程有50%的几率失败
        if (Math.random() - 0.5 > 0) {
            Proposer.printInfo("ACCEPTER_" + name, "ACCEPT", "NO RESPONSE");
            return false;
        }
        Proposer.printInfo("ACCEPTER_" + name, "ACCEPT", "OK");
        return last.equals(proposal);
    }
}
