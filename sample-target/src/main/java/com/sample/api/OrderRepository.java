package com.sample.api;

import com.sample.billing.CommercialInvoiceDraft;
import com.sample.catalog.Order;
import com.sample.fulfillment.OrderFulfillmentContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

/**
 * Order persistence port for checkout, invoicing, and fulfillment linkage.
 */
public interface OrderRepository {

    void save(Order order);

    boolean saveInvoiceDraft(CommercialInvoiceDraft draft);

    CommercialInvoiceDraft findDraftByOrderId(String orderId);

    List<String> findOpenOrderIds(String regionCode);

    void attachFulfillmentContext(String orderId, OrderFulfillmentContext context);

    BigDecimal sumOutstandingForAccount(String accountId);

    /**
     * Returns true when the order id is non-blank.
     */
    default boolean isValidOrderId(String orderId) {
        return orderId != null && !orderId.isBlank();
    }

    /**
     * Returns true when the account id is non-blank.
     */
    default boolean isValidAccountId(String accountId) {
        return accountId != null && !accountId.isBlank();
    }

    /**
     * Saves an order only when it is non-null.
     */
    default void saveIfPresent(Order order) {
        if (order != null) {
            save(order);
        }
    }

    /**
     * Persists an invoice draft when non-null.
     */
    default boolean saveInvoiceDraftIfPresent(CommercialInvoiceDraft draft) {
        if (draft == null) {
            return false;
        }
        return saveInvoiceDraft(draft);
    }

    /**
     * Finds open order ids for a region or returns an empty list when region is blank.
     */
    default List<String> findOpenOrderIdsOrEmpty(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return Collections.emptyList();
        }
        List<String> ids = findOpenOrderIds(regionCode);
        return ids == null ? Collections.emptyList() : ids;
    }

    /**
     * Attaches fulfillment context when order id and context are valid.
     */
    default void attachFulfillmentContextIfValid(String orderId, OrderFulfillmentContext context) {
        if (isValidOrderId(orderId) && context != null) {
            attachFulfillmentContext(orderId, context);
        }
    }

    /**
     * Returns outstanding balance for an account or zero when account id is invalid.
     */
    default BigDecimal safeOutstandingForAccount(String accountId) {
        if (!isValidAccountId(accountId)) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal outstanding = sumOutstandingForAccount(accountId);
        return outstanding == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : outstanding.max(BigDecimal.ZERO);
    }
}
