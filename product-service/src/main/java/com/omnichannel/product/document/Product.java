package com.omnichannel.product.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Document("products")
public class Product {

    public record Variant(String sku, String name, BigDecimal price, Map<String, String> attributes) {
    }

    @Id
    private String id;

    private String name;
    private String description;

    @Indexed
    private String categoryId;

    @Indexed
    private String brandId;

    private boolean active = true;
    private List<Variant> variants = List.of();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
    public String getBrandId() { return brandId; }
    public void setBrandId(String brandId) { this.brandId = brandId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<Variant> getVariants() { return variants; }
    public void setVariants(List<Variant> variants) { this.variants = variants; }
}
