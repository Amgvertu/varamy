package info.prorabka.varamy.repository;

import info.prorabka.varamy.entity.FeedbackMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackMessageRepository extends JpaRepository<FeedbackMessage, Long> {
}
