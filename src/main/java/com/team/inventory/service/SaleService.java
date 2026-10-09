package com.team.inventory.service;

import com.team.inventory.dto.CheckoutProduct;
import com.team.inventory.dto.SaleRequest;
import com.team.inventory.dto.SaleResponse;
import com.team.inventory.model.Sale;
import com.team.inventory.model.SaleItem;
import com.team.inventory.model.User;
import com.team.inventory.repository.ProductStockRepository;
import com.team.inventory.repository.SaleRepository;
import com.team.inventory.repository.SaleRequestKeyRepository;
import com.team.inventory.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Records a sale and takes the sold items out of stock (story 5).
 *
 * Everything happens in ONE database transaction: if any step fails (unknown product, not enough
 * stock, database error), nothing is saved and no stock changes.
 *
 * Two cashiers selling the last item at the same moment: each product row is locked
 * (SELECT ... FOR UPDATE) before its stock is checked, so the second sale waits for the first to
 * finish and then sees the updated stock. The stock UPDATE is also conditional
 * (WHERE quantity >= amount) as a second safety net, so stock can never go below zero.
 *
 * Retries: the checkout page sends a random request key with each sale. The key is saved in the
 * same transaction as the sale, so if the response is lost and the page retries with the same key,
 * the original sale is returned instead of a second one being recorded.
 */
@Service
public class SaleService {

    /** Largest value the DECIMAL(10, 2) `total` column can hold. */
    private static final BigDecimal MAX_TOTAL = new BigDecimal("99999999.99");

    private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final SaleRepository sales;
    private final ProductStockRepository products;
    private final SaleRequestKeyRepository requestKeys;
    private final UserRepository users;
    private final TransactionTemplate transaction;

    public SaleService(SaleRepository sales, ProductStockRepository products, SaleRequestKeyRepository requestKeys,
                       UserRepository users, PlatformTransactionManager transactionManager) {
        this.sales = sales;
        this.products = products;
        this.requestKeys = requestKeys;
        this.users = users;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** Products with current price and stock, for the checkout page. */
    @Transactional(readOnly = true)
    public List<CheckoutProduct> checkoutProducts() {
        return products.findAll();
    }

    /** Records a sale without retry protection. */
    public SaleResponse recordSale(String cashierUsername, SaleRequest request) {
        return recordSale(cashierUsername, request, null);
    }

    /**
     * @param cashierUsername the logged-in user (from the session)
     * @param request         product ids and quantities from the checkout page
     * @param requestKey      optional key for this checkout attempt; reusing it returns the original sale
     */
    public SaleResponse recordSale(String cashierUsername, SaleRequest request, String requestKey) {
        if (requestKey != null && !VALID_KEY.matcher(requestKey).matches()) {
            throw new SaleException(HttpStatus.BAD_REQUEST, "Invalid Idempotency-Key: use 8-64 letters, digits or dashes.");
        }
        try {
            return transaction.execute(status -> recordNewSale(cashierUsername, request, requestKey));
        } catch (DuplicateKeyException keyAlreadyUsed) {
            if (requestKey == null) {
                throw keyAlreadyUsed;
            }
            // A new transaction, so we see what the earlier request committed
            return transaction.execute(status -> existingSale(cashierUsername, requestKey));
        }
    }

    private SaleResponse recordNewSale(String cashierUsername, SaleRequest request, String requestKey) {
        User cashier = findCashier(cashierUsername);

        // Claim the key first: a concurrent retry with the same key waits here until this
        // transaction ends, then gets a duplicate key and returns this sale.
        if (requestKey != null) {
            requestKeys.claim(requestKey, cashier.getId());
        }

        // Add up quantities per product, so the same product listed twice is checked against
        // stock once, for the combined amount. Sorted by id so concurrent sales always lock rows
        // in the same order (avoids deadlocks).
        Map<Long, Long> quantities = new TreeMap<>();
        for (SaleRequest.Item item : itemsOf(request)) {
            if (item == null || item.productId() == null) {
                throw new SaleException(HttpStatus.BAD_REQUEST, "Each item needs a product.");
            }
            quantities.merge(item.productId(), wholeQuantity(item.quantity()), Long::sum);
        }

        Sale sale = new Sale(cashier);
        List<SaleResponse.Line> lines = new ArrayList<>();

        for (Map.Entry<Long, Long> entry : quantities.entrySet()) {
            long productId = entry.getKey();
            long wanted = entry.getValue();

            CheckoutProduct product = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new SaleException(HttpStatus.NOT_FOUND, "Product #" + productId + " does not exist."));

            if (wanted > product.quantity() || !products.decrementStock(productId, (int) wanted)) {
                throw new SaleException(HttpStatus.CONFLICT, "Not enough stock for " + product.name()
                    + ": " + wanted + " requested, " + product.quantity() + " available.");
            }

            SaleItem saleItem = new SaleItem(productId, (int) wanted, product.price());
            sale.addItem(saleItem);
            lines.add(new SaleResponse.Line(productId, product.name(), (int) wanted,
                product.price(), saleItem.getLineTotal()));
        }

        if (sale.getTotal().compareTo(MAX_TOTAL) > 0) {
            throw new SaleException(HttpStatus.BAD_REQUEST, "Sale total is too large to record.");
        }

        Sale saved = sales.save(sale);
        if (requestKey != null) {
            requestKeys.attachSale(requestKey, saved.getId());
        }
        return new SaleResponse(saved.getId(), cashier.getUsername(), saved.getTotal(), lines, false);
    }

    /** The sale an already-used request key produced (a retry of a sale that was saved). */
    private SaleResponse existingSale(String cashierUsername, String requestKey) {
        User cashier = findCashier(cashierUsername);
        SaleRequestKeyRepository.ClaimedKey claimed = requestKeys.find(requestKey)
            .orElseThrow(SaleService::stillInProgress);
        if (claimed.userId() != cashier.getId()) {
            throw new SaleException(HttpStatus.CONFLICT, "This sale request was already used by another account.");
        }
        Sale sale = sales.findById(claimed.saleId() == null ? -1 : claimed.saleId())
            .orElseThrow(SaleService::stillInProgress);

        List<SaleResponse.Line> lines = sale.getItems().stream()
            .map(item -> new SaleResponse.Line(item.getProductId(),
                products.findById(item.getProductId()).map(CheckoutProduct::name).orElse("Product #" + item.getProductId()),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal()))
            .toList();
        return new SaleResponse(sale.getId(), cashier.getUsername(), sale.getTotal(), lines, true);
    }

    /** The key is in use by a request that hasn't finished, so we can't say yet whether it saved. */
    private static SaleException stillInProgress() {
        return new SaleException(HttpStatus.CONFLICT,
            "This sale is still being processed. Check again in a moment.", SaleException.SALE_IN_PROGRESS);
    }

    private User findCashier(String username) {
        return users.findByUsername(username)
            .orElseThrow(() -> new SaleException(HttpStatus.UNAUTHORIZED, "Your account was not found. Please log in again."));
    }

    /** 3 or 3.0 -> 3; anything else (0, -1, 1.5, missing, huge) is rejected. */
    private static long wholeQuantity(BigDecimal quantity) {
        try {
            if (quantity != null) {
                long value = quantity.longValueExact();   // throws for 1.5 or out-of-range values
                if (value >= 1 && value <= Integer.MAX_VALUE) {
                    return value;
                }
            }
        } catch (ArithmeticException notWhole) {
            // fall through
        }
        throw new SaleException(HttpStatus.BAD_REQUEST, "Quantity must be a whole number of at least 1.");
    }

    private static List<SaleRequest.Item> itemsOf(SaleRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new SaleException(HttpStatus.BAD_REQUEST, "A sale needs at least one item.");
        }
        return request.items();
    }
}
