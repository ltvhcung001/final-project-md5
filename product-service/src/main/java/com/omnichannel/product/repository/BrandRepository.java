package com.omnichannel.product.repository;

import com.omnichannel.product.document.Brand;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface BrandRepository extends MongoRepository<Brand, String> {
}
