package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.v2.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {

    /** 获取待执行任务 */
    @Query("SELECT t FROM AgentTask t WHERE t.nextRunAt IS NOT NULL AND t.nextRunAt <= :now AND t.status = 'PENDING' ORDER BY t.priority DESC")
    List<AgentTask> findPendingTasks(@Param("now") LocalDateTime now);

    /** 按用户查询 */
    List<AgentTask> findByUserIdOrderByCreatedAtDesc(String userId);

    /** 按状态查询 */
    List<AgentTask> findByStatusOrderByPriorityDesc(String status);

    /** 按类型查询 */
    List<AgentTask> findByTypeAndStatus(String type, String status);

    /** 定时任务 */
    List<AgentTask> findByScheduleTypeAndStatus(String scheduleType, String status);

    /** 用户活跃任务 */
    @Query("SELECT t FROM AgentTask t WHERE t.userId = :userId AND t.status IN ('PENDING', 'RUNNING') ORDER BY t.priority DESC")
    List<AgentTask> findActiveByUser(@Param("userId") String userId);

    /** 统计各类型任务数 */
    @Query("SELECT t.type, COUNT(t) FROM AgentTask t GROUP BY t.type ORDER BY COUNT(t) DESC")
    List<Object[]> countByType();

    /** 统计各状态任务数 */
    @Query("SELECT t.status, COUNT(t) FROM AgentTask t GROUP BY t.status")
    List<Object[]> countByStatus();
}
