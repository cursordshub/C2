package com.sample.catalog;

import com.sample.api.ProductCatalog;
import com.sample.api.TaxCalculator;
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
class CatalogBrowseServiceTest {

    // M2-generated infra (Mockito only — no PowerMock). M3 adds SUT coverage tests only.

    @Mock
    private ProductCatalog productCatalog;

    @Mock
    private TaxCalculator taxCalculator;

    private PricingService pricingService;

    private CatalogBrowseService sut;

    @BeforeEach
    void setUp() {
        pricingService = buildPricingService();
        stubCollaborators();
        sut = new CatalogBrowseService(productCatalog, taxCalculator, pricingService);
    }

    /** Test-data builder — 7 fields (medium fixture). */
    private PricingService buildPricingService() {
        return new PricingService(
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                false
        );
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
        lenient().when(taxCalculator.computeTax(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurcharge(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDuty(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.isRegionTaxExempt(anyString())).thenReturn(true);
        lenient().when(taxCalculator.isValidTaxableAmount(any())).thenReturn(true);
        lenient().when(taxCalculator.computeTaxIfValid(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeRegionalSurchargeIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.computeImportDutyIfValid(anyString(), any())).thenReturn(BigDecimal.TEN);
        lenient().when(taxCalculator.totalTaxWithSurcharge(any(), any(BillingContext.class))).thenReturn(BigDecimal.TEN);
    }

    @Test
    void infraIsReady() {
        assertNotNull(sut);
    }

}
