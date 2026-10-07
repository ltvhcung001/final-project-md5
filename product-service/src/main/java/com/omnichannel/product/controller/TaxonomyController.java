package com.omnichannel.product.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.product.document.Brand;
import com.omnichannel.product.document.Category;
import com.omnichannel.product.dto.ProductDtos.BrandRequest;
import com.omnichannel.product.dto.ProductDtos.BrandResponse;
import com.omnichannel.product.dto.ProductDtos.CategoryRequest;
import com.omnichannel.product.dto.ProductDtos.CategoryResponse;
import com.omnichannel.product.repository.BrandRepository;
import com.omnichannel.product.repository.CategoryRepository;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Categories and brands. Reads are public, writes are ADMIN only (enforced by the gateway). */
@RestController
public class TaxonomyController {

    private final CategoryRepository categories;
    private final BrandRepository brands;

    public TaxonomyController(CategoryRepository categories, BrandRepository brands) {
        this.categories = categories;
        this.brands = brands;
    }

    @GetMapping("/api/categories")
    public ApiResponse<List<CategoryResponse>> listCategories() {
        return ApiResponse.ok(categories.findAll().stream().map(CategoryResponse::of).toList());
    }

    @PostMapping("/api/categories")
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        Category c = new Category();
        c.setName(req.name());
        c.setParentId(req.parentId());
        return ApiResponse.ok(CategoryResponse.of(categories.save(c)));
    }

    @PutMapping("/api/categories/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryRequest req) {
        Category c = categories.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        c.setName(req.name());
        c.setParentId(req.parentId());
        return ApiResponse.ok(CategoryResponse.of(categories.save(c)));
    }

    @DeleteMapping("/api/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        categories.deleteById(id);
        return ApiResponse.ok();
    }

    @GetMapping("/api/brands")
    public ApiResponse<List<BrandResponse>> listBrands() {
        return ApiResponse.ok(brands.findAll().stream().map(BrandResponse::of).toList());
    }

    @PostMapping("/api/brands")
    public ApiResponse<BrandResponse> createBrand(@Valid @RequestBody BrandRequest req) {
        Brand b = new Brand();
        b.setName(req.name());
        b.setLogoUrl(req.logoUrl());
        return ApiResponse.ok(BrandResponse.of(brands.save(b)));
    }

    @PutMapping("/api/brands/{id}")
    public ApiResponse<BrandResponse> updateBrand(@PathVariable String id, @Valid @RequestBody BrandRequest req) {
        Brand b = brands.findById(id).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        b.setName(req.name());
        b.setLogoUrl(req.logoUrl());
        return ApiResponse.ok(BrandResponse.of(brands.save(b)));
    }

    @DeleteMapping("/api/brands/{id}")
    public ApiResponse<Void> deleteBrand(@PathVariable String id) {
        brands.deleteById(id);
        return ApiResponse.ok();
    }
}
