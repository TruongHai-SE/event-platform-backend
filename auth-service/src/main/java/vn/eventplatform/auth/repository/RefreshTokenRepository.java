package vn.eventplatform.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.auth.domain.Account;
import vn.eventplatform.auth.domain.RefreshToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByAccount_IdAndIsRevokedFalse(UUID accountId);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.isRevoked = true WHERE r.account.id = :accountId AND r.isRevoked = false")
    int revokeAllByAccountId(@Param("accountId") UUID accountId);

    void deleteByAccount(Account account);
}
