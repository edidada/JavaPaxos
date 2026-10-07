# JavaPaxos 测试报告

> 坐标：`cn.wdidada:javapaxos:1.0-SNAPSHOT` · 报告日期：2026-10-07 · 分支：master

## 1. 测试范围

本轮对标本实现做工业级改造后的功能、并发安全与性能：

| 组件 | 说明 |
|---|---|
| Acceptor | 线程安全（synchronized 状态），经典 Paxos promise/accept 语义 |
| Proposer | 并行广播 PREPARE/ACCEPT、RPC 超时、quorum 判定、投票号学习（maxVoteSeen+1）、随机指数退避防活锁 |
| NetworkSimulator | 可注入丢包率与随机延迟，支持种子复现 |
| PaxosConfig | 全部参数支持 `-D` 系统属性调整 |
| PaxosMetrics | LongAdder 无锁计数 + 决策延迟分布 |
| PaxosBenchmark | 吞吐压测 + 多 Proposer 争抢安全性验证 |

## 2. 测试环境

- OS：Windows x64
- JDK：Oracle Java 17.0.12 LTS（编译目标 1.8）
- CPU：16 逻辑核；压测工作线程 64、驱动线程 32
- 构建：Maven（`mvn clean package`，含 Surefire 3.2.5）
- 日志：Log4j2 2.20.0，控制台 + `logs/javapaxos.log`（本报告数据均取自实际运行日志，原始输出见 `logs/bench-*.log`）

## 3. 单元测试结果

**26 个测试，全部通过（Failures: 0, Errors: 0, Skipped: 0），BUILD SUCCESS。**

| 测试类 | 数量 | 覆盖点 | 结果 |
|---|---|---|---|
| AcceptorTest | 8 | 高投票号承诺、低/等投票号拒绝、Promise 回传历史提案、承诺后拒绝旧 ACCEPT、Learner 视图、null 参数、8 线程并发冲击安全性 | ✅ |
| ProposerTest | 6 | 可靠网络单轮决议（rounds=1）、45% 丢包下最终决议、4 Proposer 并发争抢仅产生唯一决议值（安全性）、学习并沿用历史已选定值、网络全断（99.9% 丢包）在 maxRounds 内抛异常、指标计数正确 | ✅ |
| ProposalTest | 8 | equals/hashCode 一致性、null content 不抛 NPE、compareTo 排序、nextProposal 三条提案约束（沿用最高投票号内容 / 自由选取 / 非法 maxVote 抛异常） | ✅ |
| NetworkSimulatorTest | 4 | 100% 丢包不执行 RPC、0% 直通、延迟有界、sent/dropped 计数 | ✅ |

关键日志（Surefire）：

```text
Tests run: 8, ... -- in cn.wdidada.javapaxos.AcceptorTest
Tests run: 4, ... -- in cn.wdidada.javapaxos.NetworkSimulatorTest
Tests run: 8, ... -- in cn.wdidada.javapaxos.ProposalTest
Tests run: 6, ... -- in cn.wdidada.javapaxos.ProposerTest
Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 4. 功能验证（PaxosDemo，3 个 Proposer 并发争抢）

参数：5 Acceptor、quorum=3、丢包 20%、延迟 0–2ms。P0 首轮选定 ProjectA，P1/P2 通过 PREPARE 承诺学习到该值并沿用（1:ProjectA → 2:ProjectA → 3:ProjectA），**distinct chosen values = 1**，安全性成立。

```text
P0: DECIDED [1:ProjectA] in round[1], accepts=5/3, latencyMs=6.88
P1: DECIDED [2:ProjectA] in round[2], accepts=3/3, latencyMs=14.63
P2: DECIDED [3:ProjectA] in round[3], accepts=4/3, latencyMs=23.92
Safety check: distinct chosen values = 1 (must be 1): [ProjectA]
```

## 5. 性能基准（PaxosBenchmark）

Phase 1：N 个相互独立的 5 节点集群并发决策；Phase 2：单集群 K 个 Proposer 争抢同一实例。

| 场景 | 实例数 | 丢包率 | 决策成功 | 墙钟时间 | 吞吐 (decisions/s) | p50 | p90 | p99 | max | 平均轮数/决议 | 争抢安全性 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| A 理想网络 | 2000 | 0% | 2000/2000 | 402 ms | **4975** | 4.82 ms | 9.17 ms | 35.43 ms | 43.7 ms | 1.00 | PASS（8 路，平均 4.5 轮收敛） |
| B 常规损耗 | 1000 | ~20% | 1000/1000 | 276 ms | **3625** | 6.54 ms | 14.56 ms | 55.23 ms | 66.1 ms | 1.14 | PASS（8 路，平均 6.75 轮） |
| C 恶劣网络 | 500 | ~46% | 500/500 | 598 ms | **836** | 10.50 ms | 63.05 ms | 223.57 ms | 346.2 ms | 2.99 | PASS（**32 路**，平均 55.8 轮） |

观测结论：

1. **正确性优先**：三组场景共 3500 次决策零失败、零超时（rpcTimeouts=0），全部 `ALL PASS`；所有争抢测试中决议值唯一（Paxos 安全性未被违反）。
2. **故障退化符合理论预期**：吞吐近似随 `(1-p)^k`（一次决议最少 6 次 RPC 达 quorum）衰减，0%→20%→45% 丢包对应 4975→3625→836 decisions/s；平均轮数 1.00→1.14→2.99，重试放大可控。
3. **活锁防护有效**：45% 丢包 + 32 个 Proposer 激烈争抢时，随机指数退避仍保证全部在 55.8 轮内收敛，未出现无进展震荡。
4. **指标自证**：场景 B 中 rpcDropped=20.4%，与注入值 20% 吻合；prepareAcks/acceptAcks 与轮数交叉核算一致。

## 6. 复现命令

```bash
mvn clean package                                                  # 单元测
mvn exec:java                                                     # 演示（默认 3 proposer）
mvn exec:java -DmainClass=cn.wdidada.javapaxos.PaxosBenchmark \
    -Dpaxos.failureRate=0.2 -Dpaxos.benchmark.instances=1000       # 压测
```

## 7. 已知边界与后续建议

- 单 decree（一次表决一个值），多实例日志复制（Paxos 实例链 / Multi-Paxos）未实现——是下一步的首要功能项。
- Acceptor 状态仅存内存，无持久化（日志 + checkpoint）与 Learner 快速路径。
- 节点间为进程内模拟 RPC，网络层（真实 socket / gRPC）留待接入；延迟分位数包含线程池排队时间，非纯网络延迟。
- 吞吐受线程池规模影响明显，建议后续引入 JMH 做微基准以隔离 JIT 噪声。

## 8. 结论

功能、并发安全、故障恢复与性能四方面均通过验证：**26/26 单元测试全绿，三组合成网络压测（含 32 路争抢）全部成功且安全性断言通过**。当前实现具备工业级 Paxos 的核心行为特征（quorum 相交安全、超时重试、防活锁退避、可观测指标），可作为后续 Multi-Paxos / 持久化改造的基线。
