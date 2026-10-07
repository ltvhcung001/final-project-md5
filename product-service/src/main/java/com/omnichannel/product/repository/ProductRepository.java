package com.omnichannel.product.repository;

import com.omnichannel.product.document.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends MongoRepository<Product, String> {

    @Query("{ 'variants.sku': ?0 }")
    Optional<Product> findByVariantSku(String sku);

    @Query("{ 'variants.sku': { $in: ?0 } }")
    List<Product> findByVariantSkuIn(List<String> skus);
}
