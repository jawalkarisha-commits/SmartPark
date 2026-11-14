package com.smartpark.repository;

import com.smartpark.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndEnabledTrue(String role);

    @Query("""
        select count(u) from User u
        where u.role <> 'PARKING_OWNER' or exists (
            select ov.id from OwnerVerification ov
            where ov.owner = u and ov.emailVerified = true
        )
        """)
    long countRegisteredUsers();

    @Query("""
        select u from User u
        where (u.role <> 'PARKING_OWNER' or exists (
            select ov.id from OwnerVerification ov
            where ov.owner = u and ov.emailVerified = true
        ))
          and (:search is null
           or lower(u.fullName) like lower(concat('%', :search, '%'))
           or lower(u.email) like lower(concat('%', :search, '%')))
        """)
    Page<User> searchUsers(
        @Param("search") String search,
        Pageable pageable);

    @Query("""
        select u from User u
        where u.role = 'PARKING_OWNER'
          and not exists (
            select ov.id from OwnerVerification ov
            where ov.owner = u and ov.emailVerified = true
          )
        order by u.createdAt
        """)
    List<User> findUnverifiedOwnerAccounts();
}