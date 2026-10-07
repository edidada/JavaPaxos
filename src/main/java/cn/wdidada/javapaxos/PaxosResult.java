package cn.wdidada.javapaxos;

/**
 * 一次成功决策的结果快照。
 *
 * @author: WuCheng
 **/
public final class PaxosResult {

    private final String proposerName;
    private final Proposal proposal;
    private final int rounds;
    private final long latencyNanos;

    public PaxosResult(String proposerName, Proposal proposal, int rounds, long latencyNanos) {
        this.proposerName = proposerName;
        this.proposal = proposal;
        this.rounds = rounds;
        this.latencyNanos = latencyNanos;
    }

    public String getProposerName() {
        return proposerName;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public int getRounds() {
        return rounds;
    }

    public long getLatencyNanos() {
        return latencyNanos;
    }

    public double getLatencyMillis() {
        return latencyNanos / 1_000_000.0;
    }

    @Override
    public String toString() {
        return "PaxosResult{proposer=" + proposerName
                + ", decided=" + proposal
                + ", rounds=" + rounds
                + ", latencyMs=" + String.format("%.3f", getLatencyMillis()) + '}';
    }
}
