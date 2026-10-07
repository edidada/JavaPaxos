package cn.wdidada.javapaxos;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Proposal 值对象与 nextProposal 提案约束测试。
 */
public class ProposalTest {

    @Test
    public void equalityAndComparison() {
        Proposal a1 = new Proposal(1, "A");
        Proposal a1bis = new Proposal(1, "A");
        Proposal a2 = new Proposal(2, "A");
        Proposal nullContent = new Proposal(1, null);

        assertEquals(a1, a1bis);
        assertEquals(a1.hashCode(), a1bis.hashCode());
        assertTrue(a1.compareTo(a2) < 0);
        assertEquals(0, a1.compareTo(a1bis));
        // content 不同则不相等；null content 不抛异常
        assertFalse(a1.equals(nullContent));
    }

    @Test
    public void differentContentNotEqual() {
        assertTrue(!new Proposal(1, "A").equals(new Proposal(1, "B")));
    }

    @Test
    public void emptyProposalIsPlaceholder() {
        Proposal empty = new Proposal();
        assertEquals(0, empty.getVoteNumber());
        assertNull(empty.getContent());
        assertEquals("0:null", empty.toString());
    }

    @Test
    public void nullContentHashCodeDoesNotThrow() {
        new Proposal(7, null).hashCode();
    }

    @Test
    public void nextProposalReusesHighestAcceptedContent() {
        List<Proposal> promised = new ArrayList<Proposal>(
                Arrays.asList(new Proposal(1, "ProjectA"), new Proposal(2, "ProjectB")));
        Proposal next = Proposer.nextProposal(3, promised, () -> "FREE");
        assertEquals(4, next.getVoteNumber());
        assertEquals("ProjectB", next.getContent());
    }

    @Test
    public void nextProposalFallsBackToFreeChoice() {
        List<Proposal> empty = new ArrayList<Proposal>();
        Proposal next = Proposer.nextProposal(5, empty, () -> "FREE");
        assertEquals(6, next.getVoteNumber());
        assertEquals("FREE", next.getContent());

        List<Proposal> noContent = new ArrayList<Proposal>(Arrays.asList(new Proposal(2, null)));
        Proposal next2 = Proposer.nextProposal(5, noContent, () -> "FREE");
        assertEquals(6, next2.getVoteNumber());
        assertEquals("FREE", next2.getContent());
    }

    @Test(expected = IllegalStateException.class)
    public void nextProposalDetectsIllegalMaxVote() {
        List<Proposal> promised = new ArrayList<Proposal>(Arrays.asList(new Proposal(3, "X")));
        Proposer.nextProposal(3, promised, () -> "FREE");
    }

    @Test
    public void nextProposalOverloadsRemainValid() {
        assertNotNull(Proposer.nextProposal(1, new ArrayList<Proposal>()));
    }
}
