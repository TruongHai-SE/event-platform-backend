package vn.eventplatform.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.eventplatform.auth.domain.Account;
import vn.eventplatform.auth.domain.VerificationToken;
import vn.eventplatform.auth.domain.VerificationTokenType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenValueAndTokenType(String tokenValue, VerificationTokenType tokenType);

    void deleteByAccountAndTokenType(Account account, VerificationTokenType tokenType);
}
