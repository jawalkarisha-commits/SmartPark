package com.smartpark.repository;

import com.smartpark.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        select u from User u
        where :search is null
           or lower(u.fullName) like lower(concat('%', :search, '%'))
           or lower(u.email) like lower(concat('%', :search, '%'))
        """)
    Page<User> searchUsers(
        @Param("search") String search,
        Pageable pageable);
}