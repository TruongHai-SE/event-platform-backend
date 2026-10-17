package vn.eventplatform.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.eventplatform.auth.domain.SecurityPolicy;

@Repository
public interface SecurityPolicyRepository extends JpaRepository<SecurityPolicy, Short> {
}
