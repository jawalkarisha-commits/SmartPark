package com.smartpark.repository;

import com.smartpark.entity.BookingGroup;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingGroupRepository extends JpaRepository<BookingGroup, Long> {
    @EntityGraph(attributePaths = "user")
    Optional<BookingGroup> findByIdAndUser_Id(Long id, Long userId);
}
