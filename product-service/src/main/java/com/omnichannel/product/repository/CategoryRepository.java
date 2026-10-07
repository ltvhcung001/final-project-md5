package com.omnichannel.product.repository;

import com.omnichannel.product.document.Category;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CategoryRepository extends MongoRepository<Category, String> {
}
