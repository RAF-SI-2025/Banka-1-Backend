package com.banka1.transaction_service.domain.base;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


/**
 * Base JPA entity containing common fields for entities in the application.
 * Provides automatic management of primary key, optimistic locking version,
 * and creation and update timestamps.
 */

@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BaseEntityWithoutDelete {
    /** Primary key of the entity, automatically generated. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Optimistic locking version – Hibernate automatically increments the value on each update. */
    @Version
    private Long version;


    /** Creation timestamp of the entity – set automatically and cannot be changed. */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Last update timestamp of the entity – automatically refreshed on each modification. */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
