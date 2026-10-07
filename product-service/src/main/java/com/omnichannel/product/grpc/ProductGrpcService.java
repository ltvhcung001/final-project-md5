package com.omnichannel.product.grpc;

import com.omnichannel.product.document.Product;
import com.omnichannel.product.repository.ProductRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class ProductGrpcService extends ProductServiceGrpc.ProductServiceImplBase {

    private final ProductRepository products;

    public ProductGrpcService(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void getProduct(GetProductRequest request, StreamObserver<ProductReply> observer) {
        products.findByVariantSku(request.getSku())
                .flatMap(p -> toReply(p, request.getSku()))
                .ifPresentOrElse(
                        reply -> {
                            observer.onNext(reply);
                            observer.onCompleted();
                        },
                        () -> observer.onError(Status.NOT_FOUND
                                .withDescription("SKU not found: " + request.getSku()).asRuntimeException()));
    }

    @Override
    public void getProducts(GetProductsRequest request, StreamObserver<GetProductsReply> observer) {
        GetProductsReply.Builder reply = GetProductsReply.newBuilder();
        for (Product p : products.findByVariantSkuIn(request.getSkusList())) {
            for (String sku : request.getSkusList()) {
                toReply(p, sku).ifPresent(reply::addProducts);
            }
        }
        observer.onNext(reply.build());
        observer.onCompleted();
    }

    private static java.util.Optional<ProductReply> toReply(Product p, String sku) {
        return p.getVariants().stream()
                .filter(v -> v.sku().equals(sku))
                .findFirst()
                .map(v -> ProductReply.newBuilder()
                        .setProductId(p.getId())
                        .setSku(v.sku())
                        .setName(p.getName() + " - " + v.name())
                        .setPrice(v.price().toPlainString())
                        .setCurrency("VND")
                        .setActive(p.isActive())
                        .build());
    }
}
