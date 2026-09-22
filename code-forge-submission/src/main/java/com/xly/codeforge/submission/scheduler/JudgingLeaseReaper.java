package com.xly.codeforge.submission.scheduler;

import cn.hutool.json.JSONUtil;
import com.xly.codeforge.common.mq.JudgeMqConstant;
import com.xly.codeforge.submission.mapper.SubmissionMapper;
import com.xly.codeforge.submission.metrics.SubmissionMetrics;
import com.xly.codeforge.common.mq.JudgeMessage;
import com.xly.codeforge.model.entity.Submission;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 判题租约回收器（M1 自愈核心）
 *
 * <p><b>解决的问题</b>：judge 进程崩溃 / OOM / 网络中断时，提交会永久卡在 RUNNING(1)。
 * 单纯靠「本 JVM 异常兜底」覆盖不到进程级故障，故用 DB 租约 + 本回收器兜底。</p>
 *
 * <p><b>两条回收路径</b>：</p>
 * <ul>
 *   <li>主回收：捞过期 RUNNING 租约（{@code judging_lease_expires_at < NOW()}），
 *       CAS 复位为 WAITING + generation+1，再重派（自愈重判）。</li>
 *   <li>二级安全网：捞「无租约且闲置过久」的卡住行（WAITING 一直未被消费，
 *       或 M1 之前遗留的 RUNNING 僵尸），同样复位为重派 —— 覆盖「首次分发 / 重派事件丢失」。</li>
 * </ul>
 *
 * <p><b>多实例安全</b>：主回收靠 {@code FOR UPDATE SKIP LOCKED} 不抢同一批行；
 * 两条复位 SQL 都带 {@code generation} CAS，另一实例已复位（代次已动）时本实例返回 0 跳过。
 * 重复分发无害 —— 因为 judge 侧的 {@code acquireLease} 也是 CAS。</p>
 *
 * <p><b>重派落点</b>：往 {@code judge.direct} 发 {@code JudgeMessage}，由 judge 侧
 * {@code JudgeMqConsumer} 消费后调 {@code judgeSubmission(id)}。</p>
 *
 * @author xuxu
 */
@Slf4j
@Component
public class JudgingLeaseReaper {

    /**
     * 每批最多处理的提交数（防止一次扫出过量行把事务拖太长）
     */
    private static final int BATCH_SIZE = 20;

    /**
     * 二级安全网：闲置超过该秒数的「无租约卡住行」才重派。
     * 既覆盖分发丢失，又避免对刚插入、尚未被 judge 消费的 WAITING 行过度激进。
     */
    private static final int IDLE_SECONDS = 30;

    @Resource
    private SubmissionMapper submissionMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private SubmissionMetrics submissionMetrics;

    /**
     * 定时回收（默认每 5s 一次，启动后延迟 10s 跑首轮）。
     *
     * <p>DB 收集与复位在 {@link #collectAndReset()} 的同一事务里完成（行锁在事务提交前持有），
     * 重派事件在事务提交后才发布，避免拿着行锁去调远程 judge。</p>
     */
    @Scheduled(fixedDelayString = "${judge.reaper.interval-ms:5000}",
            initialDelayString = "${judge.reaper.initial-delay-ms:10000}")
    public void recover() {
        List<Long> toDispatch = collectAndReset();
        for (Long submissionId : toDispatch) {
            rabbitTemplate.convertAndSend(JudgeMqConstant.EXCHANGE, JudgeMqConstant.ROUTE, JSONUtil.toJsonStr(new JudgeMessage(submissionId)));
        }
        if (!toDispatch.isEmpty()) {
            if (submissionMetrics != null) {
                submissionMetrics.incrementReaperRedispatch(toDispatch.size());
            }
            log.info("租约回收器重派 {} 条卡死提交", toDispatch.size());
        }
    }

    /**
     * 收集过期 / 卡死提交并原子复位为 WAITING，返回需要重派的 id 列表。
     *
     * <p>必须在事务内执行：{@code selectExpiredJudgingForUpdate} 的 {@code FOR UPDATE}
     * 行锁要到事务提交才释放，保证「选中 → 复位」之间别的实例插不进来。</p>
     *
     * @return 需要重派的提交 id（已成功复位）
     */
    @Transactional
    protected List<Long> collectAndReset() {
        List<Long> toDispatch = new ArrayList<>();

        // 主回收：过期 RUNNING 租约
        List<Submission> expired = submissionMapper.selectExpiredJudgingForUpdate(BATCH_SIZE);
        for (Submission s : expired) {
            long gen = s.getGeneration() == null ? 1L : s.getGeneration();
            if (submissionMapper.bumpGenerationAndReset(s.getId(), gen, gen + 1) == 1) {
                toDispatch.add(s.getId());
            }
        }

        // 二级安全网：无租约且闲置过久的卡住行
        List<Submission> stuck = submissionMapper.selectStuckWithoutLease(BATCH_SIZE, IDLE_SECONDS);
        for (Submission s : stuck) {
            long gen = s.getGeneration() == null ? 1L : s.getGeneration();
            if (submissionMapper.resetStuckToWaiting(s.getId(), gen) == 1) {
                toDispatch.add(s.getId());
            }
        }

        return toDispatch;
    }
}
