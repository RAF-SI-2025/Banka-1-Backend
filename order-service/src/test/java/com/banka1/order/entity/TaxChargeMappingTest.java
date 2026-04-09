package com.banka1.order.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TaxChargeMappingTest {

    @Test
    void taxCharge_exposesLazySellAndBuyTransactionRelationships() throws Exception {
        assertTransactionRelationship("sellTransaction", "sell_transaction_id");
        assertTransactionRelationship("buyTransaction", "buy_transaction_id");
    }

    @Test
    void prePersist_initializesCreatedAtWhenMissing() {
        TaxCharge taxCharge = new TaxCharge();

        taxCharge.initializeCreatedAt();

        assertThat(taxCharge.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    private void assertTransactionRelationship(String fieldName, String joinColumnName) throws Exception {
        Field field = TaxCharge.class.getDeclaredField(fieldName);
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(field.getType()).isEqualTo(Transaction.class);
        assertThat(manyToOne).isNotNull();
        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(joinColumn).isNotNull();
        assertThat(joinColumn.name()).isEqualTo(joinColumnName);
        assertThat(joinColumn.insertable()).isFalse();
        assertThat(joinColumn.updatable()).isFalse();
    }
}
