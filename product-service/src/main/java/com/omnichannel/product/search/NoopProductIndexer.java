package com.omnichannel.product.search;

import com.omnichannel.product.document.Product;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Used when Elasticsearch is disabled (small servers); search falls back to MongoDB. */
@Component
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class NoopProductIndexer implements ProductIndexer {

    @Override
    public void index(Product product) {
    }

    @Override
    public void remove(String productId) {
    }

    @Override
    public Optional<List<String>> search(String text, String categoryId, String brandId, int page, int size) {
        return Optional.empty();
    }

    @Override
    public long lastTotal() {
        return 0;
    }
}
