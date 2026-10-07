package cn.wdidada.javapaxos;

import org.apache.commons.lang3.StringUtils;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:22
 **/

public class Proposal implements Comparable<Proposal> {

    private final long voteNumber;
    private final String content;

    public Proposal(long voteNumber, String content) {
        this.voteNumber = voteNumber;
        this.content = content;
    }

    public Proposal() {
        this(0, null);
    }

    public long getVoteNumber() {
        return voteNumber;
    }

    public String getContent() {
        return content;
    }

    @Override
    public int compareTo(Proposal o) {
        return Long.compare(voteNumber, o.voteNumber);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (!(obj instanceof Proposal))
            return false;
        Proposal proposal = (Proposal) obj;
        return voteNumber == proposal.voteNumber && StringUtils.equals(content, proposal.content);
    }

    @Override
    public int hashCode() {
        return PaxosDemo.HASH_FUNCTION
                .newHasher()
                .putLong(voteNumber)
                .putString(content, com.google.common.base.Charsets.UTF_8)
                .hash()
                .asInt();
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append(voteNumber)
                .append(':')
                .append(content)
                .toString();
    }
}
