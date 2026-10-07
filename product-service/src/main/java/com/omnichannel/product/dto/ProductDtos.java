package com.omnichannel.product.dto;

import com.omnichannel.product.document.Brand;
import com.omnichannel.product.document.Category;
import com.omnichannel.product.document.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class ProductDtos {

    private ProductDtos() {
    }

    public record VariantRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull @Positive BigDecimal price,
            Map<String, String> attributes) {
    }

    public record ProductRequest(
            @NotBlank String name,
            String description,
            String categoryId,
            String brandId,
            List<String> images,
            Boolean active,
            @NotEmpty @Valid List<VariantRequest> variants) {
    }

    public record ProductResponse(String id, String name, String description, String categoryId, String brandId,
                                  List<String> images, boolean active, List<Product.Variant> variants) {
        public static ProductResponse of(Product p) {
            return new ProductResponse(p.getId(), p.getName(), p.getDescription(), p.getCategoryId(),
                    p.getBrandId(), p.getImages(), p.isActive(), p.getVariants());
        }
    }

    public record CategoryRequest(@NotBlank String name, String parentId) {
    }

    public record BrandRequest(@NotBlank String name, String logoUrl) {
    }

    public record CategoryResponse(String id, String name, String parentId) {
        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getParentId());
        }
    }

    public record BrandResponse(String id, String name, String logoUrl) {
        public static BrandResponse of(Brand b) {
            return new BrandResponse(b.getId(), b.getName(), b.getLogoUrl());
        }
    }
}
