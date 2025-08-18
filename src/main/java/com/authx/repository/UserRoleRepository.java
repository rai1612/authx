package com.authx.repository;

import com.authx.model.Role;
import com.authx.model.User;
import com.authx.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    
    List<UserRole> findByUser(User user);
    
    List<UserRole> findByRole(Role role);
    
    long countByRole(Role role);
    
    boolean existsByUserAndRole(User user, Role role);
    
    void deleteByUserAndRole(User user, Role role);
}