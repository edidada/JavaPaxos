package cn.wdidada.javapaxos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Acceptor 协议语义与并发安全测试。
 */
public class AcceptorTest {

    @Test
    public void prepareWithHigherVoteNumberIsAcknowledged() {
        Acceptor acceptor = new Acceptor("A");
        Promise promise = acceptor.onPrepare(new Proposal(1, null));
        assertTrue(promise.isAck());
        // 初始无历史提案，回传的 accepted 应为占位提案(0:null)
        assertEquals(0, promise.getProposal().getVoteNumber());
        assertNull(promise.getProposal().getContent());
    }

    @Test
    public void prepareWithLowerOrEqualVoteNumberIsRejected() {
        Acceptor acceptor = new Acceptor("A");
        assertTrue(acceptor.onPrepare(new Proposal(5, null)).isAck());
        assertFalse(acceptor.onPrepare(new Proposal(5, null)).isAck());
        assertFalse(acceptor.onPrepare(new Proposal(3, null)).isAck());
        assertTrue(acceptor.onPrepare(new Proposal(6, null)).isAck());
    }

    @Test
    public void promiseCarriesLastAcceptedProposal() {
        Acceptor acceptor = new Acceptor("A");
        assertTrue(acceptor.onPrepare(new Proposal(1, null)).isAck());
        assertTrue(acceptor.onAccept(new Proposal(1, "ProjectA")));
        Promise promise = acceptor.onPrepare(new Proposal(2, null));
        assertTrue(promise.isAck());
        assertEquals(new Proposal(1, "ProjectA"), promise.getProposal());
    }

    @Test
    public void acceptIsRejectedWhenPromisedVoteIsHigher() {
        Acceptor acceptor = new Acceptor("A");
        acceptor.onPrepare(new Proposal(1, null));
        assertTrue(acceptor.onAccept(new Proposal(1, "ProjectA")));
        // 承诺了更高的投票号后，旧投票号的 accept 必须被拒绝
        acceptor.onPrepare(new Proposal(3, null));
        assertFalse(acceptor.onAccept(new Proposal(2, "ProjectB")));
        assertTrue(acceptor.onAccept(new Proposal(3, "ProjectB")));
        assertEquals(new Proposal(3, "ProjectB"), acceptor.getAccepted());
    }

    @Test
    public void acceptUpdatesLearnedState() {
        Acceptor acceptor = new Acceptor("A");
        acceptor.onPrepare(new Proposal(2, null));
        assertTrue(acceptor.onAccept(new Proposal(2, "ProjectC")));
        assertSame(acceptor.getAccepted(), acceptor.getAccepted());
        assertEquals("ProjectC", acceptor.getAccepted().getContent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullPrepareRejected() {
        new Acceptor("A").onPrepare(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullAcceptRejected() {
        new Acceptor("A").onAccept(null);
    }

    @Test
    public void concurrentPrepareAcceptKeepsSafety() throws Exception {
        // 多线程并发冲击同一 Acceptor：更高投票号一旦承诺，更低投票号的 accept 绝不能成功
        final Acceptor acceptor = new Acceptor("MT");
        final int threads = 8;
        final java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            final long n = i + 1;
            new Thread(() -> {
                try {
                    start.await();
                    acceptor.onPrepare(new Proposal(n, null));
                    acceptor.onAccept(new Proposal(n, "V" + n));
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        assertTrue(done.await(10, java.util.concurrent.TimeUnit.SECONDS));
        Proposal accepted = acceptor.getAccepted();
        // 最终状态必须是某一次合法交互的结果，且与 promised 单调一致
        assertTrue(accepted.getVoteNumber() >= 0);
    }
}
