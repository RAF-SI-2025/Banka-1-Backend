package com.banka1.order.entity;

import com.banka1.order.entity.enums.ListingType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioEntityTest {

    @Test
    void defaults_areCorrect() {
        Portfolio p = new Portfolio();

        assertThat(p.getIsPublic()).isFalse();
        assertThat(p.getPublicQuantity()).isEqualTo(0);
    }

    @Test
    void setFields_storesCorrectly() {
        Portfolio p = new Portfolio();
        p.setUserId(42L);
        p.setListingId(7L);
        p.setListingType(ListingType.STOCK);
        p.setQuantity(100);
        p.setAveragePurchasePrice(new BigDecimal("250.00"));
        p.setIsPublic(true);
        p.setPublicQuantity(50);

        assertThat(p.getUserId()).isEqualTo(42L);
        assertThat(p.getListingId()).isEqualTo(7L);
        assertThat(p.getListingType()).isEqualTo(ListingType.STOCK);
        assertThat(p.getQuantity()).isEqualTo(100);
        assertThat(p.getAveragePurchasePrice()).isEqualByComparingTo("250.00");
        assertThat(p.getIsPublic()).isTrue();
        assertThat(p.getPublicQuantity()).isEqualTo(50);
    }

    @Test
    void updateLastModified_setsTimestamp() {
        Portfolio p = new Portfolio();
        assertThat(p.getLastModified()).isNull();

        p.updateLastModified();

        assertThat(p.getLastModified()).isNotNull();
        assertThat(p.getLastModified()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void allListingTypes_supported() {
        for (ListingType type : ListingType.values()) {
            Portfolio p = new Portfolio();
            p.setListingType(type);
            assertThat(p.getListingType()).isEqualTo(type);
        }
    }

    @Test
    void publicQuantity_canBeSetIndependentlyOfIsPublic() {
        Portfolio p = new Portfolio();
        p.setPublicQuantity(200);

        assertThat(p.getPublicQuantity()).isEqualTo(200);
        assertThat(p.getIsPublic()).isFalse();
    }
}
