package cn.wdidada.javapaxos;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.apache.commons.lang3.StringUtils;

/**
 * @description:
 * @author: WuCheng
 * @create: 2020-04-19 21:22
 **/

public class Proposal implements Comparable<Proposal> {

    public static final HashFunction HASH_FUNCTION = Hashing.murmur3_32();

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
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Proposal))
            return false;
        Proposal proposal = (Proposal) obj;
        return voteNumber == proposal.voteNumber && StringUtils.equals(content, proposal.content);
    }

    @Override
    public int hashCode() {
        return HASH_FUNCTION
                .newHasher()
                .putLong(voteNumber)
                .putString(Strings.nullToEmpty(content), Charsets.UTF_8)
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
