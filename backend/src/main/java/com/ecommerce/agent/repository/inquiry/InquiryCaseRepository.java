package com.ecommerce.agent.repository.inquiry;

import com.ecommerce.agent.model.inquiry.InquiryCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InquiryCaseRepository extends JpaRepository<InquiryCase, Long> {

    Optional<InquiryCase> findByCaseNo(String caseNo);

    List<InquiryCase> findByOwnerIdOrderByUpdatedAtDesc(String ownerId);

    List<InquiryCase> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT c FROM InquiryCase c WHERE c.ownerId = :ownerId AND " +
            "(LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.contactEmail) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "ORDER BY c.updatedAt DESC")
    List<InquiryCase> searchMine(@Param("ownerId") String ownerId, @Param("keyword") String keyword);

    @Query("SELECT c FROM InquiryCase c WHERE " +
            "LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(c.contactEmail) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY c.updatedAt DESC")
    List<InquiryCase> searchAll(@Param("keyword") String keyword);
}
