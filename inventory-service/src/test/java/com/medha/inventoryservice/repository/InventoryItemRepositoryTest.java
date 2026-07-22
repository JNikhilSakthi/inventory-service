package com.medha.inventoryservice.repository;

import com.medha.inventoryservice.domain.InventoryItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class InventoryItemRepositoryTest {

    @Autowired
    private InventoryItemRepository repository;

    @Test
    void findBySku_returnsItem_whenExists() {
        InventoryItem item = new InventoryItem();
        item.setSku("SKU-100");
        item.setProductName("Widget");
        item.setQuantityOnHand(20);
        item.setReorderThreshold(5);
        item.setUnitPrice(new BigDecimal("9.99"));
        repository.saveAndFlush(item);

        Optional<InventoryItem> found = repository.findBySku("SKU-100");

        assertTrue(found.isPresent());
        assertEquals("Widget", found.get().getProductName());
        assertEquals(20, found.get().getQuantityOnHand());
    }

    @Test
    void existsBySku_returnsFalse_whenNotPresent() {
        assertFalse(repository.existsBySku("NOPE"));
    }

    @Test
    void existsBySku_returnsTrue_whenPresent() {
        InventoryItem item = new InventoryItem();
        item.setSku("SKU-200");
        item.setProductName("Gadget");
        item.setQuantityOnHand(5);
        item.setReorderThreshold(2);
        item.setUnitPrice(new BigDecimal("4.50"));
        repository.saveAndFlush(item);

        assertTrue(repository.existsBySku("SKU-200"));
    }
}
