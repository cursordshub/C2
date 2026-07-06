package com.sample.billing;

import com.sample.api.CurrencyConverter;
import com.sample.api.DiscountPolicy;
import com.sample.api.ProductCatalog;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingEngineTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private CurrencyConverter currencyConverter;

    @Mock
    private DiscountPolicy discountPolicy;

    private PricingEngine sut;

    @BeforeEach
    void setUp() {
        stubCollaborators();
        sut = new PricingEngine(productCatalog, currencyConverter, discountPolicy);
    }

    /** Lenient Mockito stubs — collaborators pre-wired for SUT tests. */
    private void stubCollaborators() {
        lenient().when(productCatalog.getBasePrice(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.getBulkPrice(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.isActiveSku(anyString())).thenReturn(true);
        lenient().when(productCatalog.resolveCategory(anyString())).thenReturn("stub-id");
        lenient().when(productCatalog.isValidSku(anyString())).thenReturn(true);
        lenient().when(productCatalog.isPositiveQuantity(anyInt())).thenReturn(true);
        lenient().when(productCatalog.safeBasePrice(anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.priceForQuantity(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(productCatalog.isPurchasable(anyString())).thenReturn(true);
        lenient().when(productCatalog.resolveCategoryOrDefault(anyString(), anyString())).thenReturn("stub-id");
        lenient().when(productCatalog.lineTotal(anyString(), anyInt())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.convertToLocal(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.convertFromLocal(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.currentExchangeRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConverter.isValidCurrency(anyString())).thenReturn(true);
        lenient().when(currencyConverter.isValidAmount(any())).thenReturn(true);
        lenient().when(currencyConverter.convertToLocalIfValid(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.convertFromLocalIfValid(any(), anyString())).thenReturn(BigDecimal.TEN);
        lenient().when(currencyConverter.safeExchangeRate(anyString(), anyString())).thenReturn(BigDecimal.ONE);
        lenient().when(currencyConverter.roundForBilling(any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyDiscount(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyTierDiscount(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.isPromotionActive(anyString())).thenReturn(true);
        lenient().when(discountPolicy.isValidSubtotal(any())).thenReturn(true);
        lenient().when(discountPolicy.applyDiscountIfValid(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.applyTierDiscountIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.isActivePromotion(anyString())).thenReturn(true);
        lenient().when(discountPolicy.discountSavings(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(discountPolicy.effectiveSubtotal(anyString(), any())).thenReturn(BigDecimal.TEN);
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
