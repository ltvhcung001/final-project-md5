package com.omnichannel.product.search;

import com.omnichannel.product.document.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "search.elasticsearch.enabled", havingValue = "true")
public class ElasticProductIndexer implements ProductIndexer {

    private static final Logger log = LoggerFactory.getLogger(ElasticProductIndexer.class);

    private final ElasticsearchOperations operations;
    private final ThreadLocal<Long> total = ThreadLocal.withInitial(() -> 0L);

    public ElasticProductIndexer(ElasticsearchOperations operations) {
        this.operations = operations;
    }

    @Override
    public void index(Product p) {
        try {
            ensureIndex();
            ProductSearchDoc doc = new ProductSearchDoc();
            doc.setId(p.getId());
            doc.setName(p.getName());
            doc.setDescription(p.getDescription());
            doc.setCategoryId(p.getCategoryId());
            doc.setBrandId(p.getBrandId());
            doc.setActive(p.isActive());
            doc.setSkus(p.getVariants().stream().map(Product.Variant::sku).toList());
            operations.save(doc);
        } catch (Exception e) {
            // MongoDB stays the source of truth; search falls back to it when the index is stale or down
            log.warn("Could not index product {}: {}", p.getId(), e.getMessage());
        }
    }

    @Override
    public void remove(String productId) {
        try {
            operations.delete(productId, ProductSearchDoc.class);
        } catch (Exception e) {
            log.warn("Could not remove product {} from index: {}", productId, e.getMessage());
        }
    }

    @Override
    public Optional<List<String>> search(String text, String categoryId, String brandId, int page, int size) {
        try {
            ensureIndex();
            Criteria c = new Criteria("active").is(true)
                    .and(new Criteria("name").matches(text).or("description").matches(text));
            if (categoryId != null && !categoryId.isBlank()) {
                c = c.and("categoryId").is(categoryId);
            }
            if (brandId != null && !brandId.isBlank()) {
                c = c.and("brandId").is(brandId);
            }
            var hits = operations.search(new CriteriaQuery(c, PageRequest.of(page, size)), ProductSearchDoc.class);
            total.set(hits.getTotalHits());
            return Optional.of(hits.getSearchHits().stream().map(SearchHit::getId).toList());
        } catch (Exception e) {
            log.warn("Elasticsearch unavailable, falling back to MongoDB: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long lastTotal() {
        return total.get();
    }

    private void ensureIndex() {
        IndexOperations ops = operations.indexOps(ProductSearchDoc.class);
        if (!ops.exists()) {
            ops.createWithMapping();
        }
    }
}
