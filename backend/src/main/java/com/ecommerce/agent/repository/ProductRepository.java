package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByEnabledTrueOrderByNameAsc();

    List<Product> findByCategoryAndEnabledTrue(String category);

    List<Product> findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(String name, String sku);

    @Query("""
            SELECT p FROM Product p
            WHERE p.enabled = true AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.url) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            ORDER BY p.updatedAt DESC
            """)
    List<Product> searchEnabledProducts(@Param("keyword") String keyword);

    Optional<Product> findByUrl(String url);

    long countByEnabledTrue();

    @Modifying
    @Transactional
    @Query("DELETE FROM Product p WHERE p.url NOT LIKE '%/product-detail/%' OR p.enabled = false")
    int deleteNonProductEntries();

    // 清除认证徽章假图
    @Modifying
    @Transactional
    @Query("UPDATE Product p SET p.imageUrl = NULL WHERE p.imageUrl = 'https://sc04.alicdn.com/kf/H118ee5207f09449ab9912b17e2b59bebw.png'")
    int clearBadgeImage();
}
