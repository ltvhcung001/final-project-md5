package com.omnichannel.product.service;

import com.omnichannel.common.api.PageResponse;
import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.product.document.Product;
import com.omnichannel.product.dto.ProductDtos.ProductRequest;
import com.omnichannel.product.dto.ProductDtos.ProductResponse;
import com.omnichannel.product.repository.ProductRepository;
import com.omnichannel.product.search.ProductIndexer;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ProductRepository products;
    private final MongoTemplate mongo;
    private final ProductIndexer indexer;

    public ProductService(ProductRepository products, MongoTemplate mongo, ProductIndexer indexer) {
        this.products = products;
        this.mongo = mongo;
        this.indexer = indexer;
    }

    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        assertSkusFree(p, null);
        products.save(p);
        indexer.index(p);
        return ProductResponse.of(p);
    }

    public ProductResponse update(String id, ProductRequest req) {
        Product p = find(id);
        apply(p, req);
        assertSkusFree(p, id);
        p.touch();
        products.save(p);
        indexer.index(p);
        return ProductResponse.of(p);
    }

    /** Soft delete: orders may still reference the SKU, so the product is only deactivated. */
    public void deactivate(String id) {
        Product p = find(id);
        p.setActive(false);
        p.touch();
        products.save(p);
        indexer.index(p);
    }

    public ProductResponse get(String id) {
        return ProductResponse.of(find(id));
    }

    public PageResponse<ProductResponse> search(String q, String categoryId, String brandId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);

        if (q != null && !q.isBlank()) {
            Optional<List<String>> ids = indexer.search(q, categoryId, brandId, safePage, safeSize);
            if (ids.isPresent()) {
                Map<String, Product> byId = products.findAllById(ids.get()).stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));
                List<ProductResponse> items = ids.get().stream().map(byId::get)
                        .filter(java.util.Objects::nonNull).map(ProductResponse::of).toList();
                return new PageResponse<>(items, safePage, safeSize, indexer.lastTotal());
            }
        }
        return mongoSearch(q, categoryId, brandId, safePage, safeSize);
    }

    private PageResponse<ProductResponse> mongoSearch(String q, String categoryId, String brandId, int page, int size) {
        Criteria c = Criteria.where("active").is(true);
        if (q != null && !q.isBlank()) {
            c = c.and("name").regex(Pattern.compile(Pattern.quote(q.trim()), Pattern.CASE_INSENSITIVE));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            c = c.and("categoryId").is(categoryId);
        }
        if (brandId != null && !brandId.isBlank()) {
            c = c.and("brandId").is(brandId);
        }
        Query query = new Query(c);
        long total = mongo.count(query, Product.class);
        List<ProductResponse> items = mongo.find(
                        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), Product.class)
                .stream().map(ProductResponse::of).toList();
        return new PageResponse<>(items, page, size, total);
    }

    private Product find(String id) {
        return products.findById(id).orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    private static void apply(Product p, ProductRequest req) {
        p.setName(req.name());
        p.setDescription(req.description());
        p.setCategoryId(req.categoryId());
        p.setBrandId(req.brandId());
        p.setImages(req.images() == null ? List.of() : req.images());
        p.setActive(req.active() == null || req.active());
        p.setVariants(req.variants().stream()
                .map(v -> new Product.Variant(v.sku(), v.name(), v.price(),
                        v.attributes() == null ? Map.of() : v.attributes()))
                .toList());
    }

    /** SKUs identify stock in the order service, so they must be unique across the catalog. */
    private void assertSkusFree(Product candidate, String selfId) {
        var seen = new HashSet<String>();
        for (Product.Variant v : candidate.getVariants()) {
            if (!seen.add(v.sku())) {
                throw new AppException(ErrorCode.CONFLICT, "Duplicate SKU in request: " + v.sku());
            }
            products.findByVariantSku(v.sku()).ifPresent(existing -> {
                if (!existing.getId().equals(selfId)) {
                    throw new AppException(ErrorCode.CONFLICT, "SKU already used: " + v.sku());
                }
            });
        }
    }
}
