package com.authx.repository;

import com.authx.model.WebAuthnCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebAuthnCredentialRepository extends JpaRepository<WebAuthnCredential, Long> {
    
    List<WebAuthnCredential> findByUserIdAndActiveTrue(Long userId);
    
    Optional<WebAuthnCredential> findByCredentialId(String credentialId);
    
    boolean existsByCredentialId(String credentialId);
}