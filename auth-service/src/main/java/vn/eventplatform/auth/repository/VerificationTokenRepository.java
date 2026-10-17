package vn.eventplatform.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.eventplatform.auth.domain.Account;
import vn.eventplatform.auth.domain.VerificationToken;
import vn.eventplatform.auth.domain.VerificationTokenType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findTopByTokenValueAndTokenTypeOrderByCreatedAtDesc(String tokenValue, VerificationTokenType tokenType);

    default Optional<VerificationToken> findByTokenValueAndTokenType(String tokenValue, VerificationTokenType tokenType) {
        return findTopByTokenValueAndTokenTypeOrderByCreatedAtDesc(tokenValue, tokenType);
    }

    Optional<VerificationToken> findTopByAccountAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(
            Account account,
            VerificationTokenType tokenType
    );

    List<VerificationToken> findByAccountAndTokenTypeAndIsUsedFalse(
            Account account,
            VerificationTokenType tokenType
    );

    @Modifying
    @Query("UPDATE VerificationToken v SET v.isUsed = true WHERE v.account = :account AND v.tokenType = :tokenType AND v.isUsed = false")
    int markAllUsedByAccountAndTokenType(@Param("account") Account account, @Param("tokenType") VerificationTokenType tokenType);

    void deleteByAccountAndTokenType(Account account, VerificationTokenType tokenType);
}
