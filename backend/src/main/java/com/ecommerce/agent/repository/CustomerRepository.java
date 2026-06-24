package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.v2.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /** 按国家查询 */
    List<Customer> findByCountryOrderByNameAsc(String country);

    /** 按状态查询 */
    List<Customer> findByStatusOrderByUpdatedAtDesc(String status);

    /** 按行业查询 */
    List<Customer> findByIndustryAndStatus(String industry, String status);

    /** 搜索 (名称/联系人的模糊匹配) */
    @Query("SELECT c FROM Customer c WHERE " +
           "c.name LIKE %:keyword% OR c.contactName LIKE %:keyword% OR " +
           "c.contactEmail LIKE %:keyword% OR c.website LIKE %:keyword% " +
           "ORDER BY c.updatedAt DESC")
    List<Customer> search(@Param("keyword") String keyword);

    /** 按官网去重查找 */
    Optional<Customer> findByWebsite(String website);

    /** 需要跟进的客户 (nextFollowUpAt <= now) */
    @Query("SELECT c FROM Customer c WHERE c.nextFollowUpAt IS NOT NULL AND c.nextFollowUpAt <= CURRENT_TIMESTAMP AND c.status <> 'LOST'")
    List<Customer> findDueForFollowUp();

    /** 分配给某用户的客户 */
    List<Customer> findByAssignedToOrderByUpdatedAtDesc(String assignedTo);

    /** 客户概览统计 */
    @Query("SELECT c.status, COUNT(c) FROM Customer c GROUP BY c.status")
    List<Object[]> countByStatus();

    /** 按国家统计 */
    @Query("SELECT c.country, COUNT(c) FROM Customer c GROUP BY c.country ORDER BY COUNT(c) DESC")
    List<Object[]> countByCountry();
}
