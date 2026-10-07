package com.omnichannel.product.search;

import com.omnichannel.product.document.Product;

import java.util.List;
import java.util.Optional;

/** Keeps the full-text index in sync with MongoDB (the source of truth). */
public interface ProductIndexer {

    void index(Product product);

    void remove(String productId);

    /** Returns matching product ids ordered by relevance, or empty when full-text search is unavailable. */
    Optional<List<String>> search(String text, String categoryId, String brandId, int page, int size);

    long lastTotal();
}
