package com.banka1.account_service.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@MappedSuperclass
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class BaseEntity {

    /** Primarni kljuc entiteta, automatski generisan. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Verzija za optimisticko zakljucavanje – Hibernate automatski povecava vrednost pri svakom azuriranju. */
    @Version
    private Long version;


//    /** Vreme kreiranja entiteta – postavljeno automatski i ne moze se menjati. */
//    @CreationTimestamp
//    @Column(name = "created_at", updatable = false)
//    private LocalDateTime createdAt;
//
//    /** Vreme poslednjeg azuriranja entiteta – automatski se osvezava pri svakoj izmeni. */
//    @UpdateTimestamp
//    @Column(name = "updated_at")
//    private LocalDateTime updatedAt;
}
