package app.service;

import app.entities.NotificationDelivery;
import app.entities.NotificationDeliveryStatus;
import app.repository.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Owns delivery persistence transitions so long-running side effects stay
 * outside DB transactions.
 */
@Service
@RequiredArgsConstructor
public class NotificationDeliveryTxService {
    /**
     * Repository used for all delivery state reads and writes.
     */
    private final NotificationDeliveryRepository notificationDeliveryRepository;

    /**
     * Persists a newly created pending delivery inside the caller's transaction.
     *
     * @param delivery pending delivery to persist
     * @return persisted delivery entity
     */
    @Transactional
    public NotificationDelivery createPendingDelivery(NotificationDelivery delivery) {
        return notificationDeliveryRepository.save(delivery);
    }

    /**
     * Persists a terminal audit record for invalid or unsupported messages.
     *
     * @param delivery failed audit delivery to persist
     * @return persisted delivery entity
     */
    @Transactional
    public NotificationDelivery persistFailedAudit(NotificationDelivery delivery) {
        return notificationDeliveryRepository.save(delivery);
    }

    /**
     * Finds a delivery by its internal identifier.
     *
     * @param deliveryId internal delivery identifier
     * @return matching delivery when present
     */
    @Transactional(readOnly = true)
    public Optional<@NonNull NotificationDelivery> findByDeliveryId(String deliveryId) {
        return notificationDeliveryRepository.findByDeliveryId(deliveryId);
    }

    /**
     * Reads one page of deliveries for the given status.
     *
     * @param status lifecycle status filter
     * @param pageable requested page configuration
     * @return page of matching deliveries
     */
    @Transactional(readOnly = true)
    public Page<@NonNull NotificationDelivery> findPageByStatus(
            NotificationDeliveryStatus status,
            Pageable pageable
    ) {
        return notificationDeliveryRepository.findAllByStatus(status, pageable);
    }

    /**
     * Marks a delivery as successfully sent in a dedicated transaction.
     *
     * <p>IMPORTANT: this method uses {@code REQUIRES_NEW} so it can safely run
     * after the original receive transaction has already committed.
     *
     * @param deliveryId internal delivery identifier
     * @param attemptedAt timestamp of the successful attempt
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSucceeded(String deliveryId, Instant attemptedAt) {
        notificationDeliveryRepository.findByDeliveryId(deliveryId)
                .filter(this::isActiveAttemptState)
                .ifPresent(delivery -> {
                    delivery.setAttemptCount(delivery.getAttemptCount() + 1);
                    delivery.setStatus(NotificationDeliveryStatus.SUCCEEDED);
                    delivery.setLastAttemptAt(attemptedAt);
                    delivery.setSentAt(attemptedAt);
                    delivery.setLastError(null);
                    delivery.setNextAttemptAt(null);
                    notificationDeliveryRepository.save(delivery);
                });
    }

    /**
     * Marks a failed attempt either for retry or as permanently failed.
     *
     * <p>IMPORTANT: this method uses {@code REQUIRES_NEW} so the final delivery
     * state is committed independently of the original receive transaction.
     *
     * @param deliveryId internal delivery identifier
     * @param attemptedAt timestamp of the failed attempt
     * @param error bounded error text to persist
     * @param retryable whether the failure should schedule another attempt
     * @param retryDelaySeconds delay applied before the next retry
     * @return next retry timestamp, or {@code null} when the delivery is
     *         terminal
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Instant markFailedOrRetry(
            String deliveryId,
            Instant attemptedAt,
            String error,
            boolean retryable,
            int retryDelaySeconds
    ) {
        Optional<@NonNull NotificationDelivery> optionalDelivery =
                notificationDeliveryRepository.findByDeliveryId(deliveryId)
                        .filter(this::isActiveAttemptState);
        if (optionalDelivery.isEmpty()) {
            return null;
        }

        NotificationDelivery delivery = optionalDelivery.get();
        int updatedAttemptCount = delivery.getAttemptCount() + 1;
        delivery.setAttemptCount(updatedAttemptCount);
        delivery.setLastAttemptAt(attemptedAt);
        delivery.setSentAt(null);
        delivery.setLastError(error);

        if (retryable && updatedAttemptCount < delivery.getMaxRetries()) {
            Instant nextAttemptAt = attemptedAt.plusSeconds(retryDelaySeconds);
            delivery.setStatus(NotificationDeliveryStatus.RETRY_SCHEDULED);
            delivery.setNextAttemptAt(nextAttemptAt);
            notificationDeliveryRepository.save(delivery);
            return nextAttemptAt;
        }

        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);
        notificationDeliveryRepository.save(delivery);
        return null;
    }

    /**
     * Checks whether a delivery may still transition through an active attempt.
     *
     * @param delivery persisted delivery state
     * @return {@code true} when the delivery is pending or awaiting retry
     */
    private boolean isActiveAttemptState(NotificationDelivery delivery) {
        return delivery.getStatus() == NotificationDeliveryStatus.PENDING
                || delivery.getStatus() == NotificationDeliveryStatus.RETRY_SCHEDULED;
    }
}
